// "Collapse loop with stream 'allMatch()'" "true-preview"

import java.util.Arrays;

public class Main {
  boolean find(String[][] data) {
      // Comment
      return Arrays.stream(data).flatMap(Arrays::stream).allMatch(str -> str.startsWith("xyz"));
  }
}
