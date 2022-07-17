// "Set variable type to 'HashMap<String, List<String>>'" "true"
import java.util.HashMap;
import java.util.List;

class Demo {
  void test() {
    var<caret> m = new HashMap<String, List<String>>();
  }
}
