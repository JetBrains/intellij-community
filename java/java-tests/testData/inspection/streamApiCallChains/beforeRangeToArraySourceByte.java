// "Replace with Arrays.stream()" "false"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(byte[] array) {
    IntStream.<caret>range(1, 5).mapToObj(x -> array[x]).collect(Collectors.toList());
  }
}