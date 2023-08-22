// "Call 'toArray(new X[0])'" "true-preview"
import java.util.List;
import java.util.Objects;

class Test {
  static <T> boolean contains(T needle, T... haystack) {
    for (final T t : haystack) {
      if (Objects.equals(t, needle)) {
        return true;
      }
    }
    return false;
  }

  void test() {
    class X {}
    List<X> iterable = Arrays.asList(new X());
    System.out.println(contains(new X(), iterable.toArray(new X[0])));
  }
}