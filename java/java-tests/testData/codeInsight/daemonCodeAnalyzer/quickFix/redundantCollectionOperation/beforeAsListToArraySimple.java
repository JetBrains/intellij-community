// "Replace with 'clone()'" "true"
import java.util.Arrays;

class Test {
  String[] arr;

  String[] get() {
    return Arrays.asList(arr).toAr<caret>ray(new String[0]);
  }
}