// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  void test(X x) {
      int i;
      switch (x) {
          case A:
              i = 1;
              break;
          case B:
              i = 2;
              break;
          case C:
              i = 3;
              break;
          default:
              throw new IllegalArgumentException();
      }
      System.out.println(i);
  }

  enum X {
    A, B, C
  }
}