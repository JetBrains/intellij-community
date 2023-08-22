import java.util.*;

class Test {

  void foo() {
    process(Collections.singletonMap<caret>("bar", "baz"));
  }

  void process(Map<String, String> model) {
  }
}