// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  int test(X x) {
      switch (x) {
          case A:
              return 1;
          case B:
              return 2;
          case C:
              return 3;
          default:
              throw new IllegalArgumentException();
      }
  }

  enum X {
    A, B, C
  }
}