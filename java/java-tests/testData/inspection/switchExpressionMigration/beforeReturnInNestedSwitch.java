// "Replace with 'switch' expression" "false"
class SwitchExpressionMigration {
  static int getId(String name, int n) {
    <caret>switch (name) {
      case "value1":
        return 1;
      case "value2":
        switch (n) {
          case 1:
            return 10;
        }
        return 20;
      default:
        return 3;
    }
  }
}