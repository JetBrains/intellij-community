// "Fix all 'Statement can be replaced with enhanced 'switch'' problems in file" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m1(int n, String s) {
    String v;
    if<caret>(n == 0) {
      v =  "foo";
    } else {
      v =  "bar";
    }
    return v;
  }

  private static String m2(int n, String s) {
    String v;
    if<caret>(n != 0) {
      v =  "foo";
    } else {
      v =  "bar";
    }
    return v;
  }

  private static String m3(int n, String s) {
    if<caret>(n != 0) {
      return "foo";
    } else {
      return "bar";
    }
  }

  private static String m3(int n, String s) {
    long l = System.currentTimeMillis();
    if<caret>(n != 0) {
      l = 12l;
    } else {
      l = 22l;
    }
  }
}