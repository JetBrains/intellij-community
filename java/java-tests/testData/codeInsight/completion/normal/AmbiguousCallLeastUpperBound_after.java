class Main {
  String foo(int x) { return "1";}
  StringBuilder foo(String x) { return new StringBuilder();}

  void test() {
    foo(1.25).length()<caret>
  }
}