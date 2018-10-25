// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.function.*;
import java.util.*;

public class Main {
  void test(List<String> list) {
    return list.size() > 2 &&
           list.stream().an<caret>yMatch(s -> s.isEmpty());
  }

  void test2(List<String> list) {
    return list.size() > 2 &&
           (list.stream().anyMatch(s -> s.isEmpty()) || list.size() < 10);
  }

  Predicate<List<String>> testLambda() {
    return list -> list.size() > 2 &&
                   list.stream().filter(s -> s.isEmpty()).count() > 2;
  }
}
