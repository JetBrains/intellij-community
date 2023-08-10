// "Replace with 'Arrays.copyOf()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get(int newLength) {
    return Arrays.copyOf(arr, newLength);
  }
}