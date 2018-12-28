// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void test(String s) {
    Float<caret> f = 0.0f;
    boolean inf = f.isInfinite();
    boolean nan = f.isNaN();
  }
}