// "Collapse loop with stream 'forEach()'" "true-preview"

import java.util.Collection;

public class Test {
  void test(Object obj) {
      ((Collection<String>) obj).forEach(System.out::println);
  }
}
