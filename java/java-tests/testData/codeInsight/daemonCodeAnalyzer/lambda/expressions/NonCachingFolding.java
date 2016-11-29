import java.util.function.Function;
import java.util.*;

class Test {
  void m(Set<String> i) {
    final List<String> getters = new ArrayList<String>(map(i, new Func<caret>tion<String, String>() {
      @Override
      public String apply(String propertyName) {
        return propertyName;
      }
    }));
  }


  public static <T,V> List<V> map(Iterable<? extends T> iterable, Function<T, V> mapping) {
    return null;
  }

  public static <T,V> List<V> map(Collection<? extends T> iterable, Function<T, V> mapping) {
    return null;
  }
}
