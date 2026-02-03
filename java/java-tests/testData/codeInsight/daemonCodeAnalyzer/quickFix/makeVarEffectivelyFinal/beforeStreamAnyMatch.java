// "Make 'hasEmpty' effectively final using stream API" "true-preview"
import java.util.*;

class X {
  void test(List<String> list) {
    boolean hasEmpty = false;
    for (String s : list) {
      if (s.isEmpty()) {
        hasEmpty = true;
        break;
      }
    }
    Runnable r = () -> System.out.println(<caret>hasEmpty);
  }
}