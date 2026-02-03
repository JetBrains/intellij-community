
class Test {
  String foo() {
    return null;
  }
  
  void bar() {
      if (foo() != null) {
          foo().toLowerCase()<caret>
      }
  }
}