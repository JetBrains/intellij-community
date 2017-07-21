// "Replace with findFirst()" "false"

import java.util.List;

// Not supported as OptionalInt lacks mapping method
public class Main {
  public int testPrimitiveMap(List<String> data) {
    for(String str : dat<caret>a) {
      if(str.startsWith("xyz")) {
        int len = str.length();
        if(len > 10) {
          return len * 2;
        }
      }
    }
    return 0;
  }
}