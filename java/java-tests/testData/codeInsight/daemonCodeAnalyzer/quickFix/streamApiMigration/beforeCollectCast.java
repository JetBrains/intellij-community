// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> getStrings(List<?> list) {
    List<String> result = new ArrayList<>();
    for (Object o : li<caret>st) {
      if (o instanceof String) {
        String s = (String) o;
        result.add(s);

      }
    }
    return result;
  }
}