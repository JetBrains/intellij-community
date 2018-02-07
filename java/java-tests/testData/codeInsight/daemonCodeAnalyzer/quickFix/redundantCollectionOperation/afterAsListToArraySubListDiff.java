// "Replace with 'Arrays.copyOfRange'" "true"
import java.util.Arrays;

class Test {
  String[] arr;

  CharSequence[] get(int from, int to) {
    return Arrays.copyOfRange(arr, from, to, CharSequence[].class);
  }
}