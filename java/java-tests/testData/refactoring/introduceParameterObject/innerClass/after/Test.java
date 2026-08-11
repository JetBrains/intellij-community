class Test {
  void foo(Param param) {
    bar(param.s());
  }

  void bar(String s){}

    record Param(String s) {
    }
}