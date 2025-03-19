import java.util.*;

class Test {

  void foo() {
    process(List.of<caret>("foo", "bar", "baz"));
  }

  void process(List<String> model) {

  }
}