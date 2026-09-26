// "Replace with 'java.util.HashSet' constructor" "true-preview"

import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
    s.stream().co<caret>llect(Collectors.toSet()).contains("abc");
  }
}