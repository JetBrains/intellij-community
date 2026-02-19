class Test {
  void foo(Param param) {
    bar(param.s());
  }

  void bar(String s){}

    private static record Param(String s) {
    }
}