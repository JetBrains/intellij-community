// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Test {
  public List<String> test(String[] data) {
    int top = Math.min(10, data.length);
    List<String> result = new ArrayList<>();
    for(int<caret> i=0; i<top; i++) {
      String item = data[i].trim();
      if(!item.isEmpty()) {
        result.add(item);
      }
    }
    return result;
  }
}
