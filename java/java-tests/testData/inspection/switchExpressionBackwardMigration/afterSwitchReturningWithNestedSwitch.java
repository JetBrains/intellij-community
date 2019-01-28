// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  private static void m(int x) {
      switch (x) {
          case 1:
              if (true) {
                  return 0;
              } else {
                  return 1;
              }
          case 2:
              return switch (1) {
                  case 1 -> {
                      break 12;
                  }
                  default -> 55;
              };
          default:
              return 12;
      }
  }
}