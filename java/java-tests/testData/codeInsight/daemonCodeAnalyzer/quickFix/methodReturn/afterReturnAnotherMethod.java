// "Make 'bar' return 'java.lang.String'" "true"
public class Foo {
  String foo() {
    return bar();
  }

  String bar() {
      return null;
  }
}
