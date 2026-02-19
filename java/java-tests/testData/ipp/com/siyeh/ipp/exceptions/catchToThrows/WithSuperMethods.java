class Child extends Parent {

  @Override
  void m() {
    try {
      System.out.println("1");
    }
    catch (Exception<caret> ignore) {
    }
  }
}

class Parent {
  void m() { }
}