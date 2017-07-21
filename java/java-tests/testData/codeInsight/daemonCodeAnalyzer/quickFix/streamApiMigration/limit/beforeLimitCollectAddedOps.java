// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  List<String> test(Map<String, List<String>> map, int limit) {
    List<String> list = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : map.<caret>entrySet()) {
      if(entry.getValue() != null) {
        for(String str : entry.getValue()) {
          if(str.contains("foo")) {
            list.add(entry.getKey());
            break;
          }
        }
      }
      if(list.size() >= limit) break;
    }
    return list;
  }
}
