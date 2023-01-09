// "Collapse loop with stream 'collect()'" "true-preview"

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
