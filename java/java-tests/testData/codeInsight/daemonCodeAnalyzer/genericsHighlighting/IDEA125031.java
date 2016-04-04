import java.util.Set;

class Test {

  public static void test(Set foo, Matcher<Iterable<String>> matcher) {
    assertThat(<error descr="'assertThat(Test.Matcher<? super java.util.Set>, java.util.Set)' in 'Test' cannot be applied to '(Test.Matcher<java.lang.Iterable<java.lang.String>>, java.util.Set)'">matcher</error>, foo);

    Matcher<Iterable<String>> b = null;
    <error descr="Incompatible types. Found: 'Test.Matcher<java.lang.Iterable<java.lang.String>>', required: 'Test.Matcher<? super java.util.Set>'">Matcher<? super Set> a = b;</error>
  }

  public static <T> void assertThat(Matcher<? super T> matcher, T actual) {}
  
  static class Matcher<K> {}
}
