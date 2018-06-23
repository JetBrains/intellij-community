// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static int m() {
    Integer<caret> i;
    i = Integer.valueOf("21");
    return i;
  }
}