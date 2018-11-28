// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      switch (n) {
          case 1 -> {
              return "a";
          }
          case 2 -> {
              return "b";
          }
      }
  }
}