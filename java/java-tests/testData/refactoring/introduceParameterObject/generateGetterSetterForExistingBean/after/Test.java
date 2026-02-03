class Test {
  void foo(Param param) {
    if (param.getI() == 0) {
        param.setI(param.getI() + 1);
    }
  }

  void bar(){
    foo(new Param(1));
  }
}