// "Rename 'x' to '_'" "false"
class Simple {
  void test() {
    // Do not suggest unnamed local, even if it's allowed; only remove variable is suggested
    int <caret>x = 0;
  }
}