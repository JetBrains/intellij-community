// "Replace lambda expression with 'Pattern.negate()'" "true"
import java.util.function.Predicate;

public class Main {
  void foo(Predicate<? super String> p) {}

  void bar(Predicate<? super String> p1) {
    foo(p1.negate());
  }
}

