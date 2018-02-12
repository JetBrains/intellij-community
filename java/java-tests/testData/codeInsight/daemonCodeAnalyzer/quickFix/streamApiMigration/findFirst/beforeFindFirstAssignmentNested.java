// "Replace with findFirst()" "false"

import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    String firstStr = "";
    OUTER:
    for(List<String> list : map.valu<caret>es()) {
      if(list != null) {
        for(String str : list) {
          if(!str.isEmpty()) {
            firstStr = str;
            break;
          }
        }
      }
    }
    System.out.println(firstStr);
  }
}