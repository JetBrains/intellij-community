// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void test(String s) {
    double d = 0d;
    if (s != null) {
      try {
        d = Double.parseDouble(s);
      }
      catch (NumberFormatException ignore) { }
    }

    final int intRating = (int) d;
  }
}