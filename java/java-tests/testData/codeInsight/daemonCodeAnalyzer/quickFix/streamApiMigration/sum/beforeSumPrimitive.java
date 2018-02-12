// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testPrimitiveMap(List<String> data) {
    int sum = 0;
    for(String str : dat<caret>a) {
      if(str.startsWith("xyz")) {
        int len = str.length();
        if(len > 10) {
          sum += len*2;
        }
      }
    }
  }
}