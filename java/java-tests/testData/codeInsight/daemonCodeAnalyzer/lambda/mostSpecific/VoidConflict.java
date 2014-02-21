class Test {

  interface I { void i_bar(); }
  interface I1<T> { T i1_bar(); }

  private void m(I i) {System.out.println(i);}
  private void <warning descr="Private method 'm(Test.I1<java.lang.String>)' is never used">m</warning>(I1<String> i1) {System.out.println(i1);}

  void test() {
    m(Test::foo);
  }

  public static int foo() {
    return 0;
  }
}

class Test1 {

  interface I { void i_bar(); }
  interface I1<T> { T i1_bar(); }

  void m(I i) { System.out.println(i);}
  void m(I1<String> i1) { System.out.println(i1);}

  void test() {
    m(Test1::foo);
  }

  public static String foo() {return "";}
}

class Test2 {

  interface I { void i_bar(); }
  interface I1<T> { T i1_bar(); }

  void m(I i) { System.out.println(i);}
  void m(I1<String> i1) { System.out.println(i1);}

  void test() {
    m(Test2::foo);
  }

  public static void foo() {}
}
