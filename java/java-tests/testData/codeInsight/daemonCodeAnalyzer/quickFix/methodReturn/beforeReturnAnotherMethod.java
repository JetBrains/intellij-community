// "Make 'bar()' return 'java.lang.String'" "true-preview"
public class Foo {
  String foo() {
    return <caret>bar();
  }

  void bar() {
  }
}
