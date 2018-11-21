// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(String s) {
        /*3*//*4*//*6*//*7*/int result/*1*//*2*/ = switch (s /*5*/ + s) {
            /*12*/
            /*8*/
            /*9*/
            /*10*/
            /*11*/
            case "a" -> 1;
            case "b" -> 2 + /*13*/ 3;
            /*14*/
            /*16*/
            default -> 0 +/*15*/ 1;
        };
    }
}