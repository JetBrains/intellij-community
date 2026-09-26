import java.util.*;

class Cast {
  void test(List<Object> list) {
    assertThat(list, allOf(hasEntry(""), hasEntry((Object) 1)));
  }

  interface Matcher<T> {
  }

  private static native <T> void assertThat(T actual, Matcher<? super T> matcher);

  @SafeVarargs
  private static native <T> Matcher<T> allOf(Matcher<? super T>... matchers);

  static native <K, V> Matcher<List<? extends V>> hasEntry(V value);
}