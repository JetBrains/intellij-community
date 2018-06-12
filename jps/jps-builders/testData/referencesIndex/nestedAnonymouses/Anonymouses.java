class Test {

  void m() {
    new Foo1(new Foo2() {
      void m() {
        new Foo3() {};
      }
    }) {
      void m1() {
        new Foo4() {};
      }

      void m2() {
        new Foo5(new Foo6() {}) {};
      }
    };
  }

}