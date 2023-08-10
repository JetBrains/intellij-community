class Test {
  void foo(Param param) {
    if (param.i() == 0) {
    }
  }

  void bar(){
    foo(new Param(1));
  }
}