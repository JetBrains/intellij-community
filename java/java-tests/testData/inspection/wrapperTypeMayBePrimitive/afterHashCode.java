// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void m() {
    char ch = 'a';
    Character.hashCode(ch);
  }
}