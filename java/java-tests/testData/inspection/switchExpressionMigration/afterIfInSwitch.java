// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  void test(int x) {
      switch (x) {
          case 1 -> {
              System.out.println("1");
          }
          case 2 -> {
              if (Math.random() > 0.5) {
                  System.out.println("2");
              } else {
                  System.out.println("3");
              }
          }
      }
  }
}