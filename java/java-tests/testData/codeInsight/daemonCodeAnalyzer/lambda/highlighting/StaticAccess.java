interface I {
  int m();
}

class XXX {

  int var;
  static int ourVar;


  static void foo() {
    I s = () -> <error descr="Non-static field 'var' cannot be referenced from a static context">var</error> + ourVar;
  }

  void bar() {
    I s = ()->var + ourVar;
  }
}
