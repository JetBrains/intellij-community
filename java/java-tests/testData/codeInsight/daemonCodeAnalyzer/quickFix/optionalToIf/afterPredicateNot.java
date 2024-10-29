// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.Optional;
import java.util.function.Predicate;

class Test {
  void test(String s) {
    Predicate<String> external = String::isEmpty;
      if (s != null) {
          String string = s.trim();
          if (!external.test(string)) use(string);
      }

      if (s != null) {
          String trimmed = s.trim();
          if (!trimmed.isEmpty()) use(trimmed);
      }

      if (s != null) {
          String x = s.trim();
          String trim = x.trim();
          if (trim.length() <= 5) use(trim);
      }
  }

  void use(String s) {

  }
}