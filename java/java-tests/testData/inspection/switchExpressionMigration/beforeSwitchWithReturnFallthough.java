// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case/*1*/ 1:/*2*/
      case/*3*/ 2:/*4*/
    /*5*/return "b";/*6*/
      case 3:
      case 4:
        return "c"
    }
    return "?";
  }
}