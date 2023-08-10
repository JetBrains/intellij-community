class Test {
  void foo(Param param) {
    bar(param.s());
  }

  void bar(String s){}

    static record Param(String s) {
    }
}