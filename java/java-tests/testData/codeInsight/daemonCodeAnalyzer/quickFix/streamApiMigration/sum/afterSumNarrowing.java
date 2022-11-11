// "Replace with sum()" "true-preview"

import java.util.Arrays;

public class Main {
  public void test(long[] list) {
    int sum = Arrays.stream(list).mapToInt(x -> (int) x).sum();
  }
}