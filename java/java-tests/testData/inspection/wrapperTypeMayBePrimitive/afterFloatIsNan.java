// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void test(String s) {
    float f = 0.0f;
    boolean inf = Float.isInfinite(f);
    boolean nan = Float.isNaN(f);
  }
}