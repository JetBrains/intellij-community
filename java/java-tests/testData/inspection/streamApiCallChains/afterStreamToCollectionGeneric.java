// "Replace with 'java.util.TreeSet' constructor" "true"

import java.util.*;
import java.util.stream.*;

class Test {
  public static <T, T1 extends T> void test(List<T1> s) {
      new TreeSet<T>(s).contains("abc");
  }
}