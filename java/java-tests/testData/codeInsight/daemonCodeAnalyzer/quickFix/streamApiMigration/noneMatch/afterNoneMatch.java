// "Replace with noneMatch()" "true"

import java.util.Arrays;

public class Main {
  boolean find(String[][] data) {
      return Arrays.stream(data).flatMap(Arrays::stream).noneMatch(str -> str.startsWith("xyz"));
  }
}
