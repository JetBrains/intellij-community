// "Replace lambda expression with 'Pattern.negate()'" "false"
import java.util.function.Predicate;

public class Main {
  Predicate<String> test(Predicate<Object> predicate) {
    return p -> !<caret>predicate.test(p);
  }
}

