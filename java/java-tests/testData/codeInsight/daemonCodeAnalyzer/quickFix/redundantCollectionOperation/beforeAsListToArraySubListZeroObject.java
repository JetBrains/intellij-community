// "Replace with 'Arrays.copyOf()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  Object[] get(int newLength) {
    return Arrays.asList(arr).subList(0, newLength).toAr<caret>ray();
  }
}