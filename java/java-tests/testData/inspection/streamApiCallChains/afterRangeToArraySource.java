// "Replace with Arrays.stream()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(Object[] array) {
    Arrays.stream(array, 1, 5).collect(Collectors.toList());
  }
}