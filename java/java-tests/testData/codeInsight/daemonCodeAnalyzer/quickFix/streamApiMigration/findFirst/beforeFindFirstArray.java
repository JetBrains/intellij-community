// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;
import java.util.Map;

public class Main {
  public int[] testMap(Map<String, List<String>> map) throws Exception {
    int[] arr = null;
    for(List<String> list : map.valu<caret>es()) {
      if(list != null) {
        arr = {list.size()};
        break;
      }
    }
    return arr;
  }
}