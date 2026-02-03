class A {
  private static final Object OBJ = new Object();

  void test() {
    B.use(OBJ);
  }
}

class B {
  static void use(Object obj) {
    System.out.println(obj);
  }
}