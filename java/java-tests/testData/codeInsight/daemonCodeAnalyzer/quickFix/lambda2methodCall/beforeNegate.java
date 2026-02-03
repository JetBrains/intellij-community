// "Replace lambda expression with 'Pattern.negate()'" "true-preview"
import java.util.function.Predicate;

public class Main {
  void foo(Predicate<? super String> p) {}

  void bar(Predicate<? super String> p1) {
    foo(t -> !<caret>p1.test(t));
  }
}

