// "Replace with findFirst()" "false"

import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    int bigSize = 0; // initial
    for(List<String> list : map.valu<caret>es()) {
      int size = list.size(); // size
      if(size/*bigger*/ > 10) {
        bigSize = size*2;
        break;
      }
    }
    System.out.println(bigSize);
  }
}