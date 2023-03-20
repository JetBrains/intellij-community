// "Make 'hasEmpty' effectively final using stream API" "false"
import java.util.*;

class X {
  void test(List<String> list) {
    boolean hasEmpty = false;
    System.out.println(hasEmpty);
    for (String s : list) {
      if (s.isEmpty()) {
        hasEmpty = true;
        break;
      }
    }
    Runnable r = () -> System.out.println(<caret>hasEmpty);
  }
}