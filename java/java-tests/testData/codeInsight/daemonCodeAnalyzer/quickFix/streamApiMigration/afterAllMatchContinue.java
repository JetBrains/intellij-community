// "Replace with allMatch()" "true"

import java.util.Arrays;

public class Main {
  boolean find(String[][] data) {
      return Arrays.stream(data).flatMap(Arrays::stream).allMatch(str -> str.startsWith("xyz"));
  }
}
