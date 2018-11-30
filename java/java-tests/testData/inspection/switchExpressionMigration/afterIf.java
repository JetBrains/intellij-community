// "Fix all 'Switch statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m1(int n, String s) {
    String v = switch (n) {
        case 0 -> "foo";
        default -> "bar";
    };
      return v;
  }

  private static String m2(int n, String s) {
    String v = switch (n) {
        default -> "foo";
        case 0 -> "bar";
    };
      return v;
  }

  private static String m3(int n, String s) {
      return switch (n) {
          default -> "foo";
          case 0 -> "bar";
      };
  }
}