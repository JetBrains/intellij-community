// "Replace with 'java.util.TreeSet' constructor" "true-preview"

import java.util.*;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
      new TreeSet<>(s).contains("abc");
  }
}