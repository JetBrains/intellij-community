// "Replace with Arrays.stream()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(Object[] array) {
    IntStream.<caret>range(1, 5).mapToObj(x -> array[x]).collect(Collectors.toList());
  }
}