// "Replace with 'min()'" "true-preview"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class StringComparator implements Comparator<String> {
  @Override
  public int compare(String o1, String o2) {
    return 0;
  }
}

class Test {
  public void test(String[] array) {
    StringComparator comparator = new StringComparator();
    Arrays.stream(array).min(comparator);
  }
}