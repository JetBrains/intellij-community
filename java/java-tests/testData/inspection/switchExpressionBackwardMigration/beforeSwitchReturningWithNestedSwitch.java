// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  private static void m(int x) {
    return switch<caret> (x){
      case 1 -> {
        if (true) {
          yield 0;
        }
        else {
          yield 1;
        }
      }
      case 2 -> switch (1) {
        case 1 -> {
          yield 12;
        }
        default -> 55;
      };
      default -> 12;
    };
  }
}