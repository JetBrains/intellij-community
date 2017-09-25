// "Avoid mutation using Stream API 'count()' operation" "true"

import java.util.*;

public class Main {
  void test(List<String> list) {
      int count = (int) list.stream().filter(s -> !s.isEmpty()).count();
      System.out.println(count);
  }
}
