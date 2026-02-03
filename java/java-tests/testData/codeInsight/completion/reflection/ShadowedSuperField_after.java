class Main {
  void foo() {
    Test.class.getField("shadowed");
  }
}

class Test extends Parent {
}

class Parent extends Grand {
  public int shadowed;
}

class Grand extends GrandGrand {
  public int shadowed;
}

class GrandGrand {
  public int shadowed;
}