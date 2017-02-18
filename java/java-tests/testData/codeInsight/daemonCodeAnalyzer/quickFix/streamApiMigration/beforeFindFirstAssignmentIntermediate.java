// "Replace with findFirst()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    List<String> firstList = new ArrayList<>();
    firstList.add("none");
    for(List<String> list : ma<caret>p.values()) {
      if(list != null) {
        firstList = list;
        break;
      }
    }
    System.out.println(firstList);
  }
}