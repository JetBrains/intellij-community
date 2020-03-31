import java.util.*;
import java.util.function.*;

class MyTest {
  void foo(String[] strings){
    final List<Consumer<String>> list = map2List(strings, str -> s -> asser<caret>tEquals(str, s));
  }

  static <T, V> List<V> map2List(T[] array, Function<? super T, ? extends V> mapper) {
    return null;
  }

  static boolean assertEquals(Object o1, Object o2) {
    return false;
  }
  static boolean assertEquals(String o1, String o2) {
    return false;
  }
}