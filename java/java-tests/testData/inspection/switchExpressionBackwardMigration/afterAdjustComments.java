// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  boolean toBoolean(int value) {
      switch (value) {
          //t1
          case 0://t2
              return false;
          //t3
          default://t4
              System.out.println("1");
              return true;
      }
  }
}