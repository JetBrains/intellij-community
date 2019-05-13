import java.util.Collection;
import java.util.List;

class Test {

  {
    Matcher<? super List<String>> m = not(empty());
  }

  static <E> Matcher<Collection<E>> empty() {
    return null;
  }

  static <T> Matcher<T> not(Matcher<T> matcher) {
    return null;
  }

  static <T> Matcher<T> not(T value) {
    return null;
  }

  static class Matcher<K> {}
}

