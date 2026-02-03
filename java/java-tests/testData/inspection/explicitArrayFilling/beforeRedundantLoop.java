// "Remove 'for' statement" "true"
import java.util.Arrays;
import java.util.List;

public class Test {

  public static int[] init(int n) {
    int[] data = new int[n];
    for (<caret>int i = 0; i < data.length; i++) {
      data[i] = 0;
    }
    return data;
  }
}