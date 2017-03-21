class Main {
  void foo() {
    Test.class.getField("shadowed");
  }
}

class Test extends Parent {
  public int shadowed;
}

class Parent {
  public int shadowed;
}