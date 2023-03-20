// "Replace with 'Arrays.copyOfRange()'" "true-preview"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get() {
    return Arrays.asList(arr).subList(1, 3).toAr<caret>ray(new String[2]);
  }
}