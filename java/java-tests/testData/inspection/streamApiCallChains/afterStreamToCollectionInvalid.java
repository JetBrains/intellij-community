// "Replace with 'java.util.TreeSet' constructor" "true"

import java.util.*;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
      new TreeSet<? extends String>(s).contains("abc");
  }
}