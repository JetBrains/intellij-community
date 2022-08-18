// "Call 'toArray(new String[0])'" "true-preview"
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

  static <T> boolean contains(String needle, String... haystack) {
    return contains((Object)needle, (Object[])haystack);
  }

  void use(String s) {
    if (contains(s, <caret>getList())) {

    }
  }
  
  native List<String> getList();
}