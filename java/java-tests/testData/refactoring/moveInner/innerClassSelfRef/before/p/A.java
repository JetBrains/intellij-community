package p;

class A {
    private static class B {
      public static B create() {
        return new B();
      }
    }
}