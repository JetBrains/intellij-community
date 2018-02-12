// "Replace with 'peek'" "false"

import java.util.List;

public class Main {
  void test(List<String> list) {
    long count = list.stream()
      .ma<caret>p(e -> {
        if(e.isEmpty()) {
          System.out.println("Empty line passed!");
          return null;
        }
        // hello
        return /* in return */ e;
      })
      .count();
    System.out.println(count);
  }
}
