// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"
package java.util.stream;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Collectors {
  public static native <T> Collector<T, ?, List<T>> toUnmodifiableList();
  public static native <T> Collector<T, ?, Set<T>> toUnmodifiableSet();
  public static native <T, K, U> Collector<T, ?, Map<K,U>> toUnmodifiableMap(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends U> valueMapper);
}

class MyFile {
  public static List<String> testList(String[] args) {
      List<String> result = new ArrayList<>();
      for (String arg: args) {
          if (arg != null) {
              result.add(arg);
          }
      }
      List<String> list = Collections.unmodifiableList(result);
    return list;
  }

  public static Set<String> testSet(String[] args) {
      Set<String> set = new HashSet<>();
      for (String arg: args) {
          if (arg != null) {
              set.add(arg);
          }
      }
      return Collections.unmodifiableSet(set);
  }

  public static Map<String, String> testMap(String[] args) {
      Map<String, String> map = new HashMap<>();
      for (String arg: args) {
          if (arg != null) {
              if (map.put(arg.trim(), arg) != null) {
                  throw new IllegalStateException("Duplicate key");
              }
          }
      }
      return Collections.unmodifiableMap(map);
  }
}