// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testPrimitiveMap(List<String> data) {
    long sum = 0;
    for(String str : da<caret>ta) {
      if(str.startsWith("xyz")) {
        int len = str.length();
        if(len > 10) {
          sum += len;
        }
      }
    }
  }
}