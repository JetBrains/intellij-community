// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testSumIntegerList(List<Integer> data) {
      int sum = data.stream().mapToInt(i -> i).sum();
  }
}