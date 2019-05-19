// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void test(String s) {
    Double<caret> d = 0d;
    if (s != null) {
      try {
        d = Double.valueOf(s);
      }
      catch (NumberFormatException ignore) { }
    }

    final int intRating = d.intValue();
  }
}