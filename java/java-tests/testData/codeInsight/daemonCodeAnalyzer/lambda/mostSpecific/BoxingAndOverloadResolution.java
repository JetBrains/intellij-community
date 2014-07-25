class Test {
  private static void m(int i) {System.out.println(i);}
  private static void <warning descr="Private method 'm(java.lang.Integer)' is never used">m</warning>(Integer i) {System.out.println(i);}

  interface I {
    void foo(int p);
  }

  static {
    I s = Test::m;
    System.out.println(s);
  }
}

class Test2 {

  static void m(Integer i) { }

  interface I1 {
    void m(int x);
  }

  interface I2 {
    void m(Integer x);
  }

  static void call(I1 i1) { System.out.println(i1);  }
  static void call(I2 i2) { System.out.println(i2); }

  static {
    call<error descr="Ambiguous method call: both 'Test2.call(I1)' and 'Test2.call(I2)' match">(Test2::m)</error>;
  }
}
