class IfWithoutThenBranch {

  static final int FOO = 1;
  void doIt() {
    int f = 0;
    <caret>if (f == FOO)
  }
}