// "Convert wrapper type to primitive" "true"
import java.util.*;

class TypeMayBePrimitive {
  private static Long executeCountQuery(List<Long> l) {
    long total = 0L;
    for (Long element : l) {
      total += element == null ? 0 : element;
    }
    return total;
  }
}