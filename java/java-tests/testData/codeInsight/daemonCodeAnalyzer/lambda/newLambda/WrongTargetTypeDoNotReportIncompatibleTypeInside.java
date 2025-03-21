class Test {
  void test() {
    Object obj = <error descr="Target type of a lambda conversion must be an interface">x -> {
      String s = x;
      x = "hello";
    }</error>;
  }
}