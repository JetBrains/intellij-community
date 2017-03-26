
import java.util.*;

class Test {
  interface Q<K> {
  }

  void f(List list, Set<List<Object>> q) {
    assertThat1(list, <error descr="'assertThat1(java.util.List, java.util.Set<? super java.util.List>)' in 'Test' cannot be applied to '(java.util.List, java.util.Set<java.util.List<java.lang.Object>>)'">q</error>);
    assertThat2(list, q);
    assertThat<error descr="Cannot resolve method 'assertThat(java.util.List, java.util.Set<java.util.List<java.lang.Object>>)'">(list, q)</error>;
  }

  private static <T> void assertThat1(T actual, Set<? super T> matcher) {}
  private static <T> void assertThat2(T actual, Set<? extends T> matcher) {}
  private static <T> void assertThat(T actual, Set<? super T> matcher) {}
  private static <T> void assertThat(T actual, List<? super T> matcher) {}
}