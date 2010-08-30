// "Remove Redundant 'else'" "false"
class a {
  void foo() {
    int a = 0;
    int b = 0;
    if (a != b) {
      a = 10;
    } else if (a + 1 == b) {
      return;
    }
    e<caret>lse {
      a = b;
    }
    a++;
  }
}

