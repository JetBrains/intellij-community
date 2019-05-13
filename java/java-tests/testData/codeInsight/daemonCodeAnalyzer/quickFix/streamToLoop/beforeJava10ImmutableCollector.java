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
    List<String> list = Arrays.stream(args).filter(Objects::nonNull)
                              .collect(Collectors.toUnmodifiab<caret>leList());
    return list;
  }

  public static Set<String> testSet(String[] args) {
    return Arrays.stream(args).filter(Objects::nonNull)
                 .collect(Collectors.toUnmodifiableSet());
  }

  public static Map<String, String> testMap(String[] args) {
    return Arrays.stream(args).filter(Objects::nonNull)
                 .collect(Collectors.toUnmodifiableMap(String::trim, Function.identity()));
  }
}