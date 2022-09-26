// "Make 'foo()' return 'java.lang.Object'" "true-preview"

class Test {

  void foo() {
    return <caret>null;
  }
}