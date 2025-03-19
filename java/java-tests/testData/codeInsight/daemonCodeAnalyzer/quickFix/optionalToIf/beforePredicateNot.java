// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.Optional;
import java.util.function.Predicate;

class Test {
  void test(String s) {
    Predicate<String> external = String::isEmpty;
    <caret>Optional.ofNullable(s)
      .map(String::trim)
      .filter(Predicate.not(external))
      .ifPresent(this::use);

    Optional.ofNullable(s)
      .map(String::trim)
      .filter(Predicate.not(String::isEmpty))
      .ifPresent(this::use);

    Optional.ofNullable(s)
      .map(x -> x.trim())
      .map(x -> x.trim())
      .filter(Predicate.not(x -> x.length() > 5))
      .ifPresent(this::use);
  }

  void use(String s) {

  }
}