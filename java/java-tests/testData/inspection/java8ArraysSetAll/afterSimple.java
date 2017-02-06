// "Replace loop with Arrays.setAll" "true"
import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<Integer> ints) {
    int[] arr = new int[ints.size()];
      Arrays.setAll(arr, ints::get);
    System.out.println(Arrays.toString(arr));
  }
}