// "Unwrap" "false"
import java.util.Arrays;

class Test {
  void test(String[] data) {
    List<String> list = Arrays.as<caret>List(data);
  }
}