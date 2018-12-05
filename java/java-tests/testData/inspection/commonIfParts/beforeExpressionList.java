// "Collapse 'if' statement" "false"

class ExpressionList {
  static void test(boolean b) {
    int i = 1, j = 0;
    if<caret> (b) {
      for (; j < 50; i++, j += i) {
        System.out.println(j);
      }
    } else {
      for (; j < 50; j += i, i++) {
        System.out.println(j);
      }
    }
  }
}