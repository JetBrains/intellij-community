// "Collapse loop with stream 'allMatch()'" "true-preview"

import java.util.Arrays;

public class Main {
  boolean find(String[][] data) {
      return Arrays.stream(data).flatMap(Arrays::stream).allMatch(str -> str.startsWith("xyz"));
  }
}
