// "Replace with 'java.util.TreeSet' constructor" "true"

import java.util.*;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
    s.str<caret>eam().collect(Collectors.toCollection(TreeSet<? extends String>::new)).contains("abc");
  }
}