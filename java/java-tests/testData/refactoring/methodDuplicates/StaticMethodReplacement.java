class StaticMethodReplacement {
  static void <caret>main(StaticMethodReplacement r) {
    r.bar();
  }

  void bar(){}

  void foo() {
    bar();
  }
}