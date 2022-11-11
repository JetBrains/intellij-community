// "Replace with collect" "true-preview"

import java.util.*;

public class Main {
  private void collect() {
    Set<String> res = new HashSet<>();
    for (String s : Collections.<String><caret>emptyList()) {
      if (!s.isEmpty())
        res.add(s);
    }
  }
}
