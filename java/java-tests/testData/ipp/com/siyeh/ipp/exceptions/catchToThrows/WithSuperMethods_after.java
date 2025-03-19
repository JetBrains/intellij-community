class Child extends Parent {

  @Override
  void m() throws Exception {
      System.out.println("1");<caret>
  }
}

class Parent {
  void m() throws Exception { }
}