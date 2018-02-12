// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void collect() {
    Set<String> res = new HashSet<>();
    for (String s : Arrays.<caret>asList("a", "b", "c")) {
      if (!s.isEmpty())
        res.add(s);
    }
  }
}
