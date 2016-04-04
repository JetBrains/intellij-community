class Test {
  void m() {
    final String s = "b";
    final Object o = null;
    class A {
      static final String t = s;
      <error descr="Inner classes cannot have static declarations">static</error> final Object j = <error descr="Non-static variable 'o' cannot be referenced from a static context">o</error>;
    }
  }

  private final String a = "A";
  static void foo() {
    System.out.println(<error descr="Non-static field 'a' cannot be referenced from a static context">a</error>);
  }
  
  void f() {
    class A4 {
      static final String t = <error descr="Non-static field 'a' cannot be referenced from a static context">a</error>;
    }
  }
}