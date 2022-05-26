// "Replace iteration with bulk 'TestClass.test()' call" "true"
package testpackage;

import java.util.*;

interface TestClass<T> {
  <S extends T> Iterable<S> test(Iterable<S> entities);
  <S extends T> S test(S entity);
}

public class Main {
  public void test(TestClass<Iterable<String>> repo, Iterable<List<String>> stringsToSave) {
      repo.test(stringsToSave);
  }
}