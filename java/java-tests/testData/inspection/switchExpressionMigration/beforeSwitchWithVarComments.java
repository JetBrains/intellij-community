// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(String s) {
        int result/*1*//*2*/;
        switch<caret>/*3*/(/*4*/s /*5*/ + s) /*6*/ {/*7*/
        case /*8*/"a"/*9*/: /*10*/result = 1;/*11*/ break/*12*/;
        case "b": result = 2 + /*13*/ 3; break;
        default/*14*/: result = 0 +/*15*/ 1;/*16*/
      }
  }
}