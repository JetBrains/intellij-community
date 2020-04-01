class A {
  public void newMethod() {}
}

class B {
  static class C extends A {
    {
      <selection>System.out.println();</selection>
    }
  }
}