// "Make 'bar' return 'java.lang.String'" "true"
public class Foo {
  void foo() {
    String s = bar();
  }

  String bar() {
      return null;
  }
}
