// "Replace with forEach" "true"

import java.util.Collection;

public class Test {
  void test(Object obj) {
      ((Collection<String>) obj).forEach(System.out::println);
  }
}
