// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Map;
import java.util.function.BiConsumer;

public class Main {
  void test(Map<String, Integer> map) {
      for (Map.Entry<String, Integer> entry : map.entrySet()) {
          String k = entry.getKey();
          Integer v = entry.getValue();
          if (k.isEmpty()) continue;
          System.out.println("Key: " + k + "; value: " + v);
      }
  }

  void test(Map<String, Integer> map, BiConsumer<String, Integer> consumer) {
    int entry = 1;
      for (Map.Entry<String, Integer> e : map.entrySet()) {
          String key = e.getKey();
          Integer value = e.getValue();
          consumer.accept(key, value);
      }
  }

  void test(Map<String, Integer> map, Map<String, Integer> otherMap) {
    int entry = 1, e = 2, key = 3, value = 4;
      for (Map.Entry<String, Integer> mapEntry : map.entrySet()) {
          String k = mapEntry.getKey();
          Integer v = mapEntry.getValue();
          otherMap.putIfAbsent(k, v);
      }
  }

  class X implements Map<String, String> {
    class Y {
      void test() {
          for (Entry<String, String> entry : X.this.entrySet()) {
              String k = entry.getKey();
              String v = entry.getValue();
              System.out.println(k + "-" + v);
          }
      }
    }
  }

  void convert(Map<Integer, ? extends List<? extends Appendable>> map) {
      for (Map.Entry<Integer, ? extends List<? extends Appendable>> entry : map.entrySet()) {
          Integer integer = entry.getKey();
          List<? extends Appendable> appendables = entry.getValue();
          System.out.println(integer);
          System.out.println(appendables);
      }
  }
}