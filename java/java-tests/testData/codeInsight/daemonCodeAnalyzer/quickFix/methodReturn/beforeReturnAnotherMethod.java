// "Make 'bar' return 'java.lang.String'" "true"
public class Foo {
  String foo() {
    return <caret>bar();
  }

  void bar() {
  }
}
