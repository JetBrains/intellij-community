// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  boolean toBoolean(int value) {
    return switch<caret> (value) {
      //t1
      case 0 -> {
        //t2
        yield false;
      }
      //t3
      default -> {
        //t4
        System.out.println("1");
        yield true;
      }
    };
  }
}