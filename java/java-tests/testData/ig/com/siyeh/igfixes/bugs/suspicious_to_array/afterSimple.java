// "Replace with 'new String[0]'" "true"
import java.util.List;

class Test {
  void test(List<String> list) {
    Integer[] integers = list.toArray(new String[0]);
  }
}