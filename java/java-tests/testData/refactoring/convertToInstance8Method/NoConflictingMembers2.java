class Test {
  int i;
  void run(int i) {}
}
class Other {
  static void <caret>run(Test test) {
    System.out.println(test.i);
    test.run(test.i);
  }
}