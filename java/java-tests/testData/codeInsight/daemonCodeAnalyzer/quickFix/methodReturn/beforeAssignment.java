// "Make 'bar()' return 'java.lang.String'" "true-preview"
public class Foo {
  void foo() {
    String s;
    <caret>s = bar();
  }

  void bar() {
  }
}
