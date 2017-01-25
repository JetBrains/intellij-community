// "Replace with toArray" "false"
import java.util.*;

public class Test {
  Object[] test(List<String[]> list) {
    List<Object> result = new LinkedList<>();
    for(String[] str : li<caret>st) {
      if(str != null) {
        Collections.addAll(result, str);
        if(result.size() > 10) break;
      }
    }
    result.sort(null);
    return result.toArray();
  }
}
