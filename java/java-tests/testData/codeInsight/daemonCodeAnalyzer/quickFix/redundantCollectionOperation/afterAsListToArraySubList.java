// "Replace with 'Arrays.copyOfRange()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get() {
    return Arrays.copyOfRange(arr, 1, 3);
  }
}