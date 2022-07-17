class Test {
  /**
   * foo comment
   *
   * @param param
   * @param s1    long1 description1
   */
  void foo(Param param, String s1) {
    bar(param.s(), s1);
  }

  void bar(String s, String s1){}

    private static record Param(String s) {
    }
}
