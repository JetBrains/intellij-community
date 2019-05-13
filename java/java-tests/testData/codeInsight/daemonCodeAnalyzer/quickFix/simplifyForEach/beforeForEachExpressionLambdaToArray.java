// "Replace with toArray" "true"

import java.util.*;

public class Main {
  private void test(List<String> strs) {
    List<String> tmp = new ArrayList<>();
    strs.stream().forEac<caret>h(x -> tmp.add(x));
    String[] arr = tmp.toArray();
  }
}
