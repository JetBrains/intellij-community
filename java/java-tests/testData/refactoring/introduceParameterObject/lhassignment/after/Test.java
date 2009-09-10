class Test {
  void foo(Param param) {
    if (param.getI() == 0) {
        param.setI(9);
    }
  }

  void bar(){
    foo(new Param(1));
  }
}