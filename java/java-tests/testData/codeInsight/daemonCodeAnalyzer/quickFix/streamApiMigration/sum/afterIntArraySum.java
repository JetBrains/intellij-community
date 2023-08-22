import java.util.Arrays;

// "Collapse loop with stream 'sum()'" "true-preview"
public class Main {
  public int sum(int[] array) {
    int sum = Arrays.stream(array).sum();
      return sum;
  }
}