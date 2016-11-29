// "Replace with 'java.util.ArrayList' constructor" "true"

import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
    s.stream().collect(Collectors.<Object>toL<caret>ist()).contains("abc");
  }
}