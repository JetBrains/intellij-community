// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.Arrays;

class Test {
  void test(boolean b) {
    <caret>if(b) {
      Arrays.asList("foo", "bar", "baz");
    } else {
      Arrays.asList("foo", "bar", "qux");
    }
  }
}