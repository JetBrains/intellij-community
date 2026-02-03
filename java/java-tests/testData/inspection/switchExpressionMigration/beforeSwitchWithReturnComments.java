// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret>/*1*/ (/*2*/n +/*3*/ n) /*4*/{/*5*/
      /*6*/case /*7*/1:/*8*/
        return "a"/*9*/;
      case 2:
        return "b";
    }
    return "?"/*10*/ + "!";
  }
}