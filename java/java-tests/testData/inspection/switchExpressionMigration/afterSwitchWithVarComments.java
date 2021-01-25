// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(String s) {
        int result/*1*//*2*/ = switch/*3*/(/*4*/s /*5*/ + s) /*6*/ {/*7*/
            case /*8*/"a"/*9*/ -> /*10*/1;/*11*/ /*12*/
            case "b" -> 2 + /*13*/ 3;
            default/*14*/ -> 0 +/*15*/ 1;/*16*/
        };
    }
}