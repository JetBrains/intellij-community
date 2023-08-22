// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  void test(X x) {
    int i = <caret>switch (x) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };
    System.out.println(i);
  }

  enum X {
    A, B, C
  }
}