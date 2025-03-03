/*
 * Copyright 2019-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jdbc.repository;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

import lombok.AllArgsConstructor;
import lombok.Data;

import lombok.NoArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactory;
import org.springframework.data.jdbc.testing.EnabledOnFeature;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.jdbc.testing.TestDatabaseFeatures;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Embedded.OnEmpty;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Very simple use cases for creation and usage of JdbcRepositories with test {@link Embedded} annotation in Entities.
 *
 * @author Bastian Wilhelm
 * @author Christoph Strobl
 * @author Mikhail Polivakha
 */
@ContextConfiguration
@Transactional
@ExtendWith(SpringExtension.class)
public class JdbcRepositoryEmbeddedIntegrationTests {

	@Configuration
	@Import(TestConfiguration.class)
	static class Config {

		@Autowired JdbcRepositoryFactory factory;

		@Bean
		Class<?> testClass() {
			return JdbcRepositoryEmbeddedIntegrationTests.class;
		}

		@Bean
		DummyEntityRepository dummyEntityRepository() {
			return factory.getRepository(DummyEntityRepository.class);
		}

		@Bean
		PersonRepository personRepository() {
			return factory.getRepository(PersonRepository.class);
		}

		@Bean
		WithDotColumnRepo withDotColumnRepo() { return factory.getRepository(WithDotColumnRepo.class);}

	}

	@Autowired NamedParameterJdbcTemplate template;
	@Autowired DummyEntityRepository repository;
	@Autowired PersonRepository personRepository;
	@Autowired WithDotColumnRepo withDotColumnRepo;

	@Test // DATAJDBC-111
	public void savesAnEntity() {

		DummyEntity entity = repository.save(createDummyEntity());

		assertThat(JdbcTestUtils.countRowsInTableWhere((JdbcTemplate) template.getJdbcOperations(), "dummy_entity",
				"id = " + entity.getId())).isEqualTo(1);
	}

	@Test // DATAJDBC-111
	public void saveAndLoadAnEntity() {

		DummyEntity entity = repository.save(createDummyEntity());

		assertThat(repository.findById(entity.getId())).hasValueSatisfying(it -> {
			assertThat(it.getId()).isEqualTo(entity.getId());
			assertThat(it.getPrefixedEmbeddable().getTest()).isEqualTo(entity.getPrefixedEmbeddable().getTest());
			assertThat(it.getPrefixedEmbeddable().getEmbeddable().getAttr())
					.isEqualTo(entity.getPrefixedEmbeddable().getEmbeddable().getAttr());
			assertThat(it.getEmbeddable().getTest()).isEqualTo(entity.getEmbeddable().getTest());
			assertThat(it.getEmbeddable().getEmbeddable().getAttr())
					.isEqualTo(entity.getEmbeddable().getEmbeddable().getAttr());
		});
	}

	@Test // DATAJDBC-111
	public void findAllFindsAllEntities() {

		DummyEntity entity = repository.save(createDummyEntity());
		DummyEntity other = repository.save(createDummyEntity());

		Iterable<DummyEntity> all = repository.findAll();

		assertThat(all)//
				.extracting(DummyEntity::getId)//
				.containsExactlyInAnyOrder(entity.getId(), other.getId());
	}

	@Test // DATAJDBC-111
	public void findByIdReturnsEmptyWhenNoneFound() {

		// NOT saving anything, so DB is empty
		assertThat(repository.findById(-1L)).isEmpty();
	}

	@Test // DATAJDBC-111
	public void update() {

		DummyEntity entity = repository.save(createDummyEntity());

		entity.getPrefixedEmbeddable().setTest("something else");
		entity.getPrefixedEmbeddable().getEmbeddable().setAttr(3L);
		DummyEntity saved = repository.save(entity);

		assertThat(repository.findById(entity.getId())).hasValueSatisfying(it -> {
			assertThat(it.getPrefixedEmbeddable().getTest()).isEqualTo(saved.getPrefixedEmbeddable().getTest());
			assertThat(it.getPrefixedEmbeddable().getEmbeddable().getAttr())
					.isEqualTo(saved.getPrefixedEmbeddable().getEmbeddable().getAttr());
		});
	}

	@Test // DATAJDBC-111
	public void updateMany() {

		DummyEntity entity = repository.save(createDummyEntity());
		DummyEntity other = repository.save(createDummyEntity());

		entity.getEmbeddable().setTest("something else");
		other.getEmbeddable().setTest("others Name");

		entity.getPrefixedEmbeddable().getEmbeddable().setAttr(3L);
		other.getPrefixedEmbeddable().getEmbeddable().setAttr(5L);

		repository.saveAll(asList(entity, other));

		assertThat(repository.findAll()) //
				.extracting(d -> d.getEmbeddable().getTest()) //
				.containsExactlyInAnyOrder(entity.getEmbeddable().getTest(), other.getEmbeddable().getTest());

		assertThat(repository.findAll()) //
				.extracting(d -> d.getPrefixedEmbeddable().getEmbeddable().getAttr()) //
				.containsExactlyInAnyOrder(entity.getPrefixedEmbeddable().getEmbeddable().getAttr(),
						other.getPrefixedEmbeddable().getEmbeddable().getAttr());
	}

	@Test // DATAJDBC-111
	public void deleteById() {

		DummyEntity one = repository.save(createDummyEntity());
		DummyEntity two = repository.save(createDummyEntity());
		DummyEntity three = repository.save(createDummyEntity());

		repository.deleteById(two.getId());

		assertThat(repository.findAll()) //
				.extracting(DummyEntity::getId) //
				.containsExactlyInAnyOrder(one.getId(), three.getId());
	}

	@Test // DATAJDBC-111
	public void deleteByEntity() {
		DummyEntity one = repository.save(createDummyEntity());
		DummyEntity two = repository.save(createDummyEntity());
		DummyEntity three = repository.save(createDummyEntity());

		repository.delete(one);

		assertThat(repository.findAll()) //
				.extracting(DummyEntity::getId) //
				.containsExactlyInAnyOrder(two.getId(), three.getId());
	}

	@Test // DATAJDBC-111
	public void deleteByList() {

		DummyEntity one = repository.save(createDummyEntity());
		DummyEntity two = repository.save(createDummyEntity());
		DummyEntity three = repository.save(createDummyEntity());

		repository.deleteAll(asList(one, three));

		assertThat(repository.findAll()) //
				.extracting(DummyEntity::getId) //
				.containsExactlyInAnyOrder(two.getId());
	}

	@Test // DATAJDBC-111
	public void deleteAll() {

		repository.save(createDummyEntity());
		repository.save(createDummyEntity());
		repository.save(createDummyEntity());

		assertThat(repository.findAll()).isNotEmpty();

		repository.deleteAll();

		assertThat(repository.findAll()).isEmpty();
	}

	@Test // DATAJDBC-370
	public void saveWithNullValueEmbeddable() {

		DummyEntity entity = repository.save(new DummyEntity());

		assertThat(JdbcTestUtils.countRowsInTableWhere((JdbcTemplate) template.getJdbcOperations(), "dummy_entity",
				"id = " + entity.getId())).isEqualTo(1);
	}

	@Test // GH-1286
	public void findOrderedByEmbeddedProperty() {

		Person first = new Person(null, "Bob", "Seattle", new PersonContacts("ddd@example.com", "+1 111 1111 11 11"));
		Person second = new Person(null, "Alex", "LA", new PersonContacts("aaa@example.com", "+2 222 2222 22 22"));
		Person third = new Person(null, "Sarah", "NY", new PersonContacts("ggg@example.com", "+3 333 3333 33 33"));

		personRepository.saveAll(List.of(first, second, third));

		Iterable<Person> fetchedPersons = personRepository.findAll(Sort.by(new Sort.Order(Sort.Direction.ASC, "personContacts.email")));

		Assertions.assertThat(fetchedPersons).containsExactly(second, first, third);
	}

	@Test // GH-1286
	public void sortingWorksCorrectlyIfColumnHasDotInItsName() {

		WithDotColumn first = new WithDotColumn(null, "Salt Lake City");
		WithDotColumn second = new WithDotColumn(null, "Istanbul");
		WithDotColumn third = new WithDotColumn(null, "Tokyo");

		withDotColumnRepo.saveAll(List.of(first, second, third));

		Iterable<WithDotColumn> fetchedPersons = withDotColumnRepo.findAll(Sort.by(new Sort.Order(Sort.Direction.ASC, "address")));

		Assertions.assertThat(fetchedPersons).containsExactly(second, first, third);
	}

	private static DummyEntity createDummyEntity() {
		DummyEntity entity = new DummyEntity();

		final CascadedEmbeddable prefixedCascadedEmbeddable = new CascadedEmbeddable();
		prefixedCascadedEmbeddable.setTest("c1");

		final Embeddable embeddable1 = new Embeddable();
		embeddable1.setAttr(1L);
		prefixedCascadedEmbeddable.setEmbeddable(embeddable1);

		entity.setPrefixedEmbeddable(prefixedCascadedEmbeddable);

		final CascadedEmbeddable cascadedEmbeddable = new CascadedEmbeddable();
		cascadedEmbeddable.setTest("c2");

		final Embeddable embeddable2 = new Embeddable();
		embeddable2.setAttr(2L);
		cascadedEmbeddable.setEmbeddable(embeddable2);

		entity.setEmbeddable(cascadedEmbeddable);

		return entity;
	}

	interface DummyEntityRepository extends CrudRepository<DummyEntity, Long> {}

	interface PersonRepository extends PagingAndSortingRepository<Person, Long>, CrudRepository<Person, Long> {}

	interface WithDotColumnRepo extends PagingAndSortingRepository<WithDotColumn, Integer>, CrudRepository<WithDotColumn, Integer> {}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	static class WithDotColumn {

		@Id
		private Integer id;
		@Column("address.city")
		private String address;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@Table("SORT_EMBEDDED_ENTITY")
	static class Person {
		@Id
		private Long id;
		private String firstName;
		private String address;

		@Embedded.Nullable
		private PersonContacts personContacts;
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	static class PersonContacts {
		private String email;
		private String phoneNumber;
	}

	@Data
	static class DummyEntity {

		@Id Long id;

		@Embedded(onEmpty = OnEmpty.USE_NULL, prefix = "PREFIX_") CascadedEmbeddable prefixedEmbeddable;

		@Embedded(onEmpty = OnEmpty.USE_NULL) CascadedEmbeddable embeddable;
	}

	@Data
	static class CascadedEmbeddable {
		String test;

		@Embedded(onEmpty = OnEmpty.USE_NULL, prefix = "PREFIX2_") Embeddable embeddable;
	}

	@Data
	static class Embeddable {
		Long attr;
	}
}
