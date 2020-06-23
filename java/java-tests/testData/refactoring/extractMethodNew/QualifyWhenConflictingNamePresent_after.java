class A {
  public void newMethod() {}
}

class B {
  static class C extends A {
    {
        B.newMethod();
    }
  }

    private static void newMethod() {
        System.out.println();
    }
}