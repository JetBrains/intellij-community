class Test {
  interface I {
    Object m();
  }

  void m(I i) {}

  {
    m<error descr="'m(Test.I)' in 'Test' cannot be applied to '(<method reference>)'">(String[]::new)</error>;
  }
}
