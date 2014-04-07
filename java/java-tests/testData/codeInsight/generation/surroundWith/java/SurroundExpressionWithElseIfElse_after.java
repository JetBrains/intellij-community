class Test {
  boolean foo() {
    return true;
  }
  
  void bar() {
    if (true) {
    }
    else if (foo()) {
        <caret>
    } else {
    }
  }
}