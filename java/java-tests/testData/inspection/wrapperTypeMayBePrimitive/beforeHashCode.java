// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void m() {
    Character<caret> ch = 'a';
    ch.hashCode();
  }
}