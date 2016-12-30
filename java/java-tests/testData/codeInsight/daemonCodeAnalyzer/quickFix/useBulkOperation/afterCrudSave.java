// "Replace iteration with bulk 'CrudRepository.save' call" "true"
package org.springframework.data.repository;

import java.io.Serializable;

interface CrudRepository<T,ID extends Serializable> {
  <S extends T> Iterable<S> save(Iterable<S> entities);
  <S extends T> S save(S entity);
}

public class Main {
  public void test(CrudRepository<CharSequence, Integer> repo, Iterable<String> stringsToSave) {
      repo.save(stringsToSave);
  }
}