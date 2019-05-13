class A {
  public void newMethod() {}
}

class B {
  static class C extends A {
    {
        B.newMethod();
    }
  }

    private void newMethod() {
        System.out.println();
    }
}