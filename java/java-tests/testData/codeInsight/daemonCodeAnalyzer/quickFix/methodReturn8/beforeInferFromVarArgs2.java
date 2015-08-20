// "Make 'bar' return 'java.util.List<java.lang.String>'" "false"
public class Foo {
  <T> java.util.List<T> foo(T... t) {
    return null;
  }

  String bar() {
    return fo<caret>o();
  }
}
