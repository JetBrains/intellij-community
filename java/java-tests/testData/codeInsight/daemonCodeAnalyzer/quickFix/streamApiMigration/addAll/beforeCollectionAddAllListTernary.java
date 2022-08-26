// "Replace with collect" "true-preview"
import java.util.*;

public class Test {
  List<String> test(List<List<String>> list) {
    List<String> result = new ArrayList<>();
    for(List<String> nested : li<caret>st) {
      if(nested != null) {
        result.addAll(nested.isEmpty() ? Collections.singleton("foo") : nested);
      }
    }
    return result;
  }
}
