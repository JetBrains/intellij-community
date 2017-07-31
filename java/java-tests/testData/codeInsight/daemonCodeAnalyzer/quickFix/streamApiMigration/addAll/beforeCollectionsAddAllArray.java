// "Replace with toArray" "true"
import java.util.*;

public class Test {
  Object[] test(List<String[]> list) {
    List<Object> result = new LinkedList<>();
    for(String[] str : li<caret>st) {
      if(str != null) {
        Collections.addAll(result, str);
      }
    }
    result.sort(null);
    return result.toArray();
  }
}
