// "Call 'toArray(new String[0])'" "false"
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

  void useOk(List<String> list, List<String> s) {
    if (contains(s, <caret>list)) {

    }
  }
}