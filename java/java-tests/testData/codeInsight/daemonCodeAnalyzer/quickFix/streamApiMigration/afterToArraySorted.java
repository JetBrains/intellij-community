// "Replace with toArray" "true"

import java.util.*;

public class Test {
  public static String[] test(String[] args) {
      String[] array = Arrays.stream(args).filter(s -> !s.isEmpty()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new);
      return array;
  }
}
