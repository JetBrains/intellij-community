// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static void m() {
    Integer<caret> i = 12;
    System.out.println(i.toString());
  }
}