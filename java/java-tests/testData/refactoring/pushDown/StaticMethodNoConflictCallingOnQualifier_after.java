
interface I {
}

class C implements I {
    public static void foo() {}
}

class Usage {
  void bar() {
    C.foo();
  }
}