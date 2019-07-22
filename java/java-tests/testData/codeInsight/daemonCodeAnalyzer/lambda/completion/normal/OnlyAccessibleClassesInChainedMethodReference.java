import java.util.Map;
import java.util.function.Function;
class Test2 {
  void test() {
    Function<Map.Entry<String, String>, String> fn = Entry::getKe<caret>x
  }
}

