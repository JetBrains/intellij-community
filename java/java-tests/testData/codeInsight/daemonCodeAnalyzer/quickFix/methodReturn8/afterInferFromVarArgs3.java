import java.util.List;

// "Make 'bar()' return 'java.util.List<java.lang.String>'" "true-preview"
public class Foo {
  <T> java.util.List<T> foo(T... t) {
    return null;
  }

  List<String> bar() {
    return foo(new String[] {""});
  }
}
