// "Replace with forEach" "false"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

  public List<String> test(Map<String, List<String>> map) {
    List<String> result = new ArrayList<>();
    outer:
    for(Map.Entry<String, List<String>> entry : map.ent<caret>rySet()) {
      if(entry.getKey().isEmpty()) continue;
      List<String> list = entry.getValue();
      if(list == null) continue;
      for(String str : list) {
        String trimmed = str.trim();
        if(trimmed.isEmpty()) continue outer;
        result.add(trimmed);
      }
    }
    return result;
  }
}