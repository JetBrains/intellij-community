// "Replace with Arrays.stream()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(int[] array) {
    IntStream.<caret>range(1, 5).map(x -> array[x]).collect(Collectors.toList());
  }
}