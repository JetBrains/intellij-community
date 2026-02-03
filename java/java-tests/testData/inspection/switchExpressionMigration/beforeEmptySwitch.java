// "Replace with enhanced 'switch' statement" "false"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
    }
  }
}