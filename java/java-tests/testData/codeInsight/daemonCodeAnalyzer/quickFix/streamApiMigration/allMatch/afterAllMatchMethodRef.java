// "Replace with allMatch()" "true-preview"

import java.util.Arrays;

public class Main {
  boolean allEmpty(String[][] data) {
      return Arrays.stream(data).flatMap(Arrays::stream).allMatch(String::isEmpty);
  }
}
