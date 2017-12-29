// "Replace with 'Arrays.copyOf'" "true"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get(int newLength) {
    return Arrays.asList(arr).subList(0, newLength).toAr<caret>ray(new String[newLength]);
  }
}