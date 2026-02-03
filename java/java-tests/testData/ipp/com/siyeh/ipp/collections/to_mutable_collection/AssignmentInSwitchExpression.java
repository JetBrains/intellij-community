import java.util.*;

class Test {

  void foo(String s) {
    List<String> tmp;
    List<String> list = switch (s) {
      case "singleton" -> tmp = process(Collections.singletonList(<caret>"foo"));
      default -> new ArrayList<>();
    };
  }

  List<String> process(List<String> model) {
    return model;
  }
}