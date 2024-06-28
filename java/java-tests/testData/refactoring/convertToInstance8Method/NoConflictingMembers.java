class Test {
  int i;
  void run() {}
}
class Other {
  static void <caret>handle(Test test) {
    System.out.println(test.i);
    test.run();
  }
}