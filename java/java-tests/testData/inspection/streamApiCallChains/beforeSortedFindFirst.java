// "Replace with min()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(int[] array) {
    Arrays.stream(array).sorted().<caret>findFirst();
  }
}