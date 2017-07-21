// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public String testNestedForLoop(int[] data, List<String> info) {
    for(int val : da<caret>ta) {
      for(int x = 0; x <= val; x++) {
        String str = info.get(x);
        if(!str.isEmpty()) {
          return str;
        }
      }
    }
    return null;
  }
}