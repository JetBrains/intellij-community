import java.util.Arrays;

// "Collapse loop with stream 'forEach()'" "true-preview"
public class Main {
  public void test(int[] arr) {
      Arrays.stream(arr).filter(i -> i > 0).forEach(System.out::println);
  }
}