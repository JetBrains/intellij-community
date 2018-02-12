// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

  public List<String> test(Map<String, List<String>> map) {
    List<String> result = new ArrayList<>();
    for(Map.Entry<String, List<String>> entry : map.en<caret>trySet()) {
      if(entry.getKey().isEmpty()) continue;
      List<String> list = entry.getValue();
      if(list == null) continue;
      for(String str : list) {
        String trimmed = str.trim();
        if(trimmed.isEmpty()) continue;
        result.add(trimmed);
      }
    }
    return result;
  }
}