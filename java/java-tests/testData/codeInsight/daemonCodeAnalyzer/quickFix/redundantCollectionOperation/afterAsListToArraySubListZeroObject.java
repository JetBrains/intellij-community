// "Replace with 'Arrays.copyOf()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  Object[] get(int newLength) {
    return Arrays.copyOf(arr, newLength, Object[].class);
  }
}