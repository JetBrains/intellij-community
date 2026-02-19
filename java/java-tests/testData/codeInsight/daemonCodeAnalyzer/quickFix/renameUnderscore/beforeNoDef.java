// "Rename unnamed variable" "false"
class X {
  void test() {
    System.out.println(<caret>_);
  }

  void other() {
    for (var _ : new int[10]) {

    }
  }
}