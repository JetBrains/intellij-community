class A {
  static final int a = 0;

  static class B {
    static final int b = 1;

    static class C {
      static final int c = 2;
    }
  }

  void test1() {
    int i = A.a;
    int j = A.a;
  }

  void test2() {
    int i = A.B.b;
    int j = A.B.b;
  }

  void test3() {
    int i = A.B.C.c;
    int j = A.B.C.c;
  }
}
