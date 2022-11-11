// "Replace with count()" "true-preview"

import java.util.*;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
    int count = 0;
    strs.stream().forEac<caret>h(x -> count++)
  }
}
