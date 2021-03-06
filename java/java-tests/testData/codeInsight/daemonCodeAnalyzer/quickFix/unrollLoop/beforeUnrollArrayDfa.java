// "Unroll loop" "true"
import java.util.Arrays;

class X {
  void test() {
    int[] array = new int[10];
    Arrays.setAll(array, i -> i);
    <caret>for (int i : array) {
      System.out.println(i);
    }
  }
}