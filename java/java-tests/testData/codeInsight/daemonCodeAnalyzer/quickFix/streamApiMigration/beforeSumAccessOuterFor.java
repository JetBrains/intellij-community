// "Replace with sum()" "false"

import java.util.List;

public class Main {
  public long testFor(List<List<String>> list) {
    long count = 0;
    for (int i = 0; i < list.size(); i++) {
      for (String s : list.g<caret>et(i)) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
          count += i;
        }
      }
    }
    return count;
  }
}