// "Make 'foo' return 'java.lang.Object'" "true"

class Test {

  void foo() {
    return <caret>null;
  }
}