// "Replace with toArray" "true"
import java.util.*;

public class Test {
  String[] test(List<String> list) {
    List<String> result = new LinkedList<>();
    for(String str : li<caret>st) {
      if(str != null) {
        result.add(str);
      }
    }
    result.sort(String.CASE_INSENSITIVE_ORDER);
    return result.toArray(new String[0]);
  }
}
