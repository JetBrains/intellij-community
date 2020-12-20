// "Collapse into loop" "false"
class X {
  void test() {
    switch(0) {
      <caret>case 1 -> {}
      case 2 -> {}
      case 3 -> {}
    }
  }
}