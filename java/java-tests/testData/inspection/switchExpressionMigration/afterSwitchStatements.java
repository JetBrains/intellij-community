// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      switch (n) {
          case 1 ->/*1*/
                  System.out.println("a"/*2*/); /*3*/
          case 2 -> System.out.println("b");
      }
  }
}