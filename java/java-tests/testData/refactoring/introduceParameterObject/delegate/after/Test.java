class Test {
    void foo(int... i) {
        foo(new Param(i));
    }

    void foo(Param param) {
    if (param.i().lenght == 0) {
    }
  }

  void bar(){
    foo(1, 2);
  }
}