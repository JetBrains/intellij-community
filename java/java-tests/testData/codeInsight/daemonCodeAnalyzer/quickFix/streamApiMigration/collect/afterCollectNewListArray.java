// "Collapse loop with stream 'toArray()'" "true-preview"

import java.util.*;

public class Test {
  public static String[] test(String[] args) {
      return Arrays.stream(args).filter(s -> !s.isEmpty()).distinct().sorted().toArray(String[]::new);
  }
}
