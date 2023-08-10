import java.util.function.Predicate;

class X {
  Predicate<String> predicate() {
    return s -> !s.isEmpty() && <caret>test(s) > 0;
  }

  int test(String s) {
    return s.length() + s.charAt(0);
  }
}