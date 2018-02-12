// "Replace with 'peek'" "true"

import java.util.List;

public class Main {
  void test(List<String> list) {
    long count = list.stream()
      .ma<caret>p(e -> {
        System.out.println(e);
        // hello
        return /* in return */ e;
      })
      .count();
    System.out.println(count);
  }
}
