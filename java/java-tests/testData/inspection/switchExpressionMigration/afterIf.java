// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
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

  private static String m3(int n, String s) {
      System.currentTimeMillis();
      long l = switch (n) {
          default -> 12l;
          case 0 -> 22l;
      };
  }
}