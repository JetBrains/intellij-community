// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  int x;
  void test(X x) {
      switch (x) {
          case A:
              this.x *= 1;
              break;
          case B:
              this.x *= 2;
              break;
          case C:
              this.x *= 3;
              break;
          default:
              throw new IllegalArgumentException();
      }
  }

  enum X {
    A, B, C
  }
}
