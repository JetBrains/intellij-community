// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static boolean m() {
    boolean b = Boolean.parseBoolean("true");
    return b;
  }
}