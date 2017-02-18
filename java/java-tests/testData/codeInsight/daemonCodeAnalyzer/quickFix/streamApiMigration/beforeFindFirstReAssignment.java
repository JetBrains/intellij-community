// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;

public class Main {
  private int getInitialSize() {return 0;}

  public void testMap(Map<String, List<String>> map) throws Exception {
    int firstSize = 10;

    System.out.println(firstSize);

    firstSize = getInitialSize();
    // loop
    for(List<String> list : map.valu<caret>es()) {
      if(list != null) {
        firstSize = list.size();
        // comment
        break;
      }
    }
    System.out.println(firstSize);
  }
}