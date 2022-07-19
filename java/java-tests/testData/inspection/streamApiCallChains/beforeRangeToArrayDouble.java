// "Replace with 'Arrays.stream()'" "true-preview"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(double[] array) {
    IntStream.<caret>range(1, 5).mapToDouble(x -> array[x]).collect(Collectors.toList());
  }
}