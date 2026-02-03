// "Replace with 'switch' expression" "false"

class NotDefenitelyAssignment1 {
  void test(int x) {
    String s;
    if (Math.random() > 0.5) {
      s = "bar";
    }
    swi<caret>tch (x) {
      case 1:s = "baz";break;
      case 2:s = "qux";break;
    }
  }
}