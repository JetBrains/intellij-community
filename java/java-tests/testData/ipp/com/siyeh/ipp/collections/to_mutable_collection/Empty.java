import java.util.*;

class Test {

  void foo() {
    process(Collections.emptyList<caret>());
  }

  void process(List<String> model) {

  }
}