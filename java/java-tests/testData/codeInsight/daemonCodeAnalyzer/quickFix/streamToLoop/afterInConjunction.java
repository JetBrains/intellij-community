// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.function.*;
import java.util.*;

public class Main {
  void test(List<String> list) {
      if (list.size() <= 2) return false;
      for (String s : list) {
          if (s.isEmpty()) {
              return true;
          }
      }
      return false;
  }

  void test2(List<String> list) {
      if (list.size() <= 2) return false;
      for (String s : list) {
          if (s.isEmpty()) {
              return true;
          }
      }
      return list.size() < 10;
  }

  Predicate<List<String>> testLambda() {
    return list -> {
        if (list.size() <= 2) return false;
        long count = 0L;
        for (String s : list) {
            if (s.isEmpty()) {
                count++;
            }
        }
        return count > 2;
    };
  }
}
