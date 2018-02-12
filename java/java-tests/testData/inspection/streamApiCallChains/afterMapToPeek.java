// "Replace with 'peek'" "true"

import java.util.List;

public class Main {
  void test(List<String> list) {
      // hello
      /* in return */
      long count = list.stream()
      .peek(System.out::println)
      .count();
    System.out.println(count);
  }
}
