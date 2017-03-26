// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static void test(String[] args) {
      System.out.println(Arrays.toString(Arrays.stream(args).filter(s -> !s.isEmpty()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new)));
  }
}
