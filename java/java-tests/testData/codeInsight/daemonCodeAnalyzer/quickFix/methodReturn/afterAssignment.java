// "Make 'bar()' return 'java.lang.String'" "true-preview"
public class Foo {
  void foo() {
    String s;
    s = bar();
  }

  String bar() {
      return null;
  }
}
