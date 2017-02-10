import java.util.Arrays;

// "Replace with sum()" "true"
public class Main {
  public int sum(int[] array) {
      int sum = Arrays.stream(array).sum();
      return sum;
  }
}