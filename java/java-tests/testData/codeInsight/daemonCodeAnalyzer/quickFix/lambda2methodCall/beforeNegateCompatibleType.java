// "Replace lambda expression with 'Pattern.negate()'" "true"
import java.util.function.Predicate;

public class Main {
  Predicate<? super String> test(Predicate<Object> predicate) {
    return p -> !<caret>predicate.test(p);
  }
}

