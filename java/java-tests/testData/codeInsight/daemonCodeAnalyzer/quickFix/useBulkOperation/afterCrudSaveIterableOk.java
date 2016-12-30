// "Replace iteration with bulk 'CrudRepository.save' call" "true"
package org.springframework.data.repository;

import java.io.Serializable;
import java.util.List;

interface CrudRepository<T,ID extends Serializable> {
  <S extends T> Iterable<S> save(Iterable<S> entities);
  <S extends T> S save(S entity);
}

public class Main {
  public void test(CrudRepository<Iterable<String>, Integer> repo, Iterable<List<String>> stringsToSave) {
      repo.save(stringsToSave);
  }
}