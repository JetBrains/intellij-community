// "Replace 'collect(toList())' with 'toList()'" "true-preview"

import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
    s.stream().collect(Collectors.toL<caret>ist()).contains("abc");
  }
}