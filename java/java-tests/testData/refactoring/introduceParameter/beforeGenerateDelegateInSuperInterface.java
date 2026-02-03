interface A {
  void xxx();
}

class B implements A {
  public void xxx() {
    System.out.println(<selection>239</selection>);
  }

  static {
    A a = new B();
    a.xxx();
  }
}