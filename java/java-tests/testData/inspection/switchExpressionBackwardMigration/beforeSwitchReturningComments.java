// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
    /*1*/return/*2*/ switch<caret> /*3*/(/*4*/x +/*5*/ x/*6*/) /*7*/ {
      /*8*/case /*9*/ 1 +/*10*/ 1 -> /*11*/{if (true /*12*/)
        /*13*/break /*14*/ 0;
      else
        break 1;
      }
      case/*15*/ 2 -> /*16*/2 +/*17*/ 2;
      case 3, 4 -> {
        System.out.println("asda");
        break 3;
      }
      /*18*/default/*19*/ -> 12 /*20*/ + 12;
    };
  }
}