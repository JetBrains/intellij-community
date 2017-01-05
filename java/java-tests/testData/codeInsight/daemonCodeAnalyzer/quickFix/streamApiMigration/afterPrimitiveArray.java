import java.util.Arrays;

// "Replace with forEach" "true"
public class Main {
  public void test(int[] arr) {
      Arrays.stream(arr).filter(i -> i > 0).forEach(System.out::println);
  }
}