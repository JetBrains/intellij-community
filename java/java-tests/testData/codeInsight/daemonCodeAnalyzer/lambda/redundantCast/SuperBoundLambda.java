import java.util.function.*;
import java.util.*;

class Y {
  <T> List<T> filter(Collection<? extends T> collection,
                     Predicate<? super T> condition) {return null;}
  boolean testString(String s) {return s.isEmpty();}
  List<Object> test(List<String> input, boolean b) {
    return b ? new ArrayList<>() : filter(input, o -> o instanceof String && testString((String)o));
  }
}
