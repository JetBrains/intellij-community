// "Make 'bar' return 'java.lang.String'" "true"
public class Foo {
  void foo() {
    String s;
    <caret>s = bar();
  }

  void bar() {
  }
}
