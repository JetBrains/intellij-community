class Test {
  void m() {
    final String s = "b";
    final Object o = null;
    class A {
      static final String t = s;
      <error descr="Inner classes cannot have static declarations">static</error> final Object j = <error descr="Non-static variable 'o' cannot be referenced from a static context">o</error>;
    }
  }
}