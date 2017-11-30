// "Replace with 'Arrays.copyOfRange'" "true"
import java.util.Arrays;

class Test {
  String[] arr;

  CharSequence[] get(int from, int to) {
    return Arrays.asList(arr).subList(from, to).toAr<caret>ray(new CharSequence[to-from]);
  }
}