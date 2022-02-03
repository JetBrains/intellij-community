// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Map;
import java.util.function.BiConsumer;

public class Main {
  void test(Map<String, Integer> map) {
    map.fo<caret>rEach((k, v) -> {
      if (k.isEmpty()) return;
      System.out.println("Key: " + k + "; value: " + v);
    });
  }

  void test(Map<String, Integer> map, BiConsumer<String, Integer> consumer) {
    int entry = 1;
    map.forEach(consumer);
  }

  void test(Map<String, Integer> map, Map<String, Integer> otherMap) {
    int entry = 1, e = 2, key = 3, value = 4;
    map.forEach(otherMap::putIfAbsent);
  }

  class X implements Map<String, String> {
    class Y {
      void test() {
        forEach((k, v) -> System.out.println(k + "-" + v));
      }
    }
  }

  void convert(Map<Integer, ? extends List<? extends Appendable>> map) {
    map.forEach((integer, appendables) -> {
      System.out.println(integer);
      System.out.println(appendables);
    });
  }
}