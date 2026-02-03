// "Replace with 'List.of()' call" "false"
import java.util.*;

class Cls {
  void test() {
    String[] array = new String[]{ "a", null, "c" };
    List<String> list = Collections.<caret>unmodifiableList(Arrays.asList(array));
  }
}