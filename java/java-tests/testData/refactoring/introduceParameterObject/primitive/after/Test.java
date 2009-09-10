class Test {
  void foo(Param param) {
    if (param.getI() == 0) {
    }
  }

  void bar(){
    foo(new Param(1));
  }
}