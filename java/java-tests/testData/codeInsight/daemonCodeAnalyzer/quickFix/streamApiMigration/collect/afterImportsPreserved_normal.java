// "Collapse loop with stream 'forEach()'" "true-preview"

import java.util.Objects;
import java.util.stream.IntStream;

public class Test {
  void collectNames() {
    int[] arr1 = {1, 2, 3};
    int[] arr2 = {1, 2, 3};
    deepEquals(arr1, arr2);
      IntStream.range(0, arr1.length).forEach(i -> {
          int a = arr1[i];
          int b = arr2[i];
          boolean result = Objects.deepEquals(a, b);
          System.out.println("result is " + result);
      });
  }
}
