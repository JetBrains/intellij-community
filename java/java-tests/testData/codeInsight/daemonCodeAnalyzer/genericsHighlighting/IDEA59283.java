class Test {

  public void doesNotCompile() {
    Container<String> container = new Container<String>();
    assertThat(container, <error descr="'assertThat(Test.Container<java.lang.String>, Test.Matcher<? super Test.Container<java.lang.String>>)' in 'Test' cannot be applied to '(Test.Container<java.lang.String>, Test.Matcher<Test.Container<capture<? super java.lang.String>>>)'">hasSomething(is("foo"))</error>);
  }

  public static class Container<T> {}

  public static <T> Matcher<Container<T>> hasSomething(Matcher<T> matcher) {
    return null;
  }

  public static <T> void assertThat(T actual, Matcher<? super T> matcher) {}

  public static <T> Matcher<? super T> is(T value) {
    return null;
  }

  public interface Matcher<T> {}
}