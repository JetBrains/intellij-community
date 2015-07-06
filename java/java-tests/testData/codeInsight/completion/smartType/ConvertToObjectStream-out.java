import java.util.Arrays;
import java.util.stream.Stream;

class Test {
  Stream<String> m(String[] a) {
    return Arrays.stream(a);<caret>
  }
}