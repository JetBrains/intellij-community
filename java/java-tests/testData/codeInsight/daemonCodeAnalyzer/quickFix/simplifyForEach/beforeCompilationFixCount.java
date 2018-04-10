// "Avoid mutation using Stream API 'count()' operation" "true"

import java.util.*;

public class Main {
  void test(List<String> list) {
    int count = 0;
    list.forEach(s -> {
      if(!s.isEmpty()) c<caret>ount++;
    });
    System.out.println(count);
  }
}
