// "Replace with 'java.util.HashSet' constructor" "true-preview"

import java.util.HashSet;
import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
      new HashSet<>(s).contains("abc");
  }
}