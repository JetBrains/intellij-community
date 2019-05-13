class Main {
  void foo() {
    Test.class.getField("<caret>");
  }
}

class Test extends Parent {
  public int shadowed;
}

class Parent {
  public int shadowed;
}