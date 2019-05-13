interface I {
  static void a() {}
}

interface J {
  void foo();
}

class Test {
  {
    J j = I::a;
  }
}