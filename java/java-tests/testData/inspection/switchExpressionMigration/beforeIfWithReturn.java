// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    if<caret>/*1*/(n /*2*/== 0 +/*cond*/ 1) /*3*/ { /*4*/
      /*5*/return /*6*/ "foo";
    }/*7*/ else/*8*/ {/*9*/
      return/*10*/ "bar"/*11*/;
    }
  }
}