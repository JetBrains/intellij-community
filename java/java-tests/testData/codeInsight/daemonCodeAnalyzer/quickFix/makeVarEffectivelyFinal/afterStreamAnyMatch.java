// "Make 'hasEmpty' effectively final using stream API" "true-preview"
import java.util.*;

class X {
  void test(List<String> list) {
    boolean hasEmpty = list.stream().anyMatch(String::isEmpty);
      Runnable r = () -> System.out.println(hasEmpty);
  }
}