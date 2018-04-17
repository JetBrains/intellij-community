// "Replace with collect" "false"

import java.util.ArrayList;
import java.util.List;

public class Main {
  List<String> test(String[][] data) {
    int i = data.length - 1;
    List<String> list = new ArrayList<>();
    do {
      fo<caret>r (String s : data[i]) {
        if (!s.isEmpty()) {
          list.add(s);
        }
      }
    } while(--i > 0);
    return list;
  }
}