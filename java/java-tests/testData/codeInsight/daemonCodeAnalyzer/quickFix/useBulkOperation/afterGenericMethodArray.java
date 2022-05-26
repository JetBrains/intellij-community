// "Replace iteration with bulk 'TestClass.test()' call" "true"
package testpackage;

import java.util.*;

interface TestClass<T> {
  <S extends T> Iterable<S> test(Iterable<S> entities);
  <S extends T> S test(S entity);
}

public class Main {
  public void test(TestClass<CharSequence> repo, String[] stringsToSave) {
      repo.test(Arrays.asList(stringsToSave));
  }
}