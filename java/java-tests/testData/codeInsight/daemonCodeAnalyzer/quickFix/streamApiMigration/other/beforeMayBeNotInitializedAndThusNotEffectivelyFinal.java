// "Replace with collect" "false"

import java.util.ArrayList;
import java.util.List;

class Main {
  public List<Integer> test(boolean b, List<String> list) {
    int s;
    if(b) {
      s = 1;
    }
    List<Integer> result = new ArrayList<>();
    for(String str : li<caret>st) {
      if(str != null) {
        result.add(s++);
      }
    }
    return result;
  }
}