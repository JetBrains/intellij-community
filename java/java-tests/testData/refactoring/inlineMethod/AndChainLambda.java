import java.util.function.Predicate;

class X {
  Predicate<String> predicate() {
    return s -> !s.isEmpty() && <caret>test(s) > 0;
  }

  int test(String s) {
    int length = s.length();
    int ch = s.charAt(0);
    return length + ch;
  }
}