// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static boolean m() {
    Boolean<caret> b = Boolean.valueOf("true");
    return b;
  }
}