// "Replace 'collect(toList())' with 'toList()'" "true"

import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
    s.stream().toList().contains("abc");
  }
}