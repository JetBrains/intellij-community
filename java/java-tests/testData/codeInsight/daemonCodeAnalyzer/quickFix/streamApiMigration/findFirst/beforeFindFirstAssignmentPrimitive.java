// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    int bigSize = 0;
    for(List<String> list : map.valu<caret>es()) {
      int size = list.size();
      if(size > 10) {
        bigSize = size;
        break;
      }
    }
    System.out.println(bigSize);
  }
}