// "Replace lambda expression with 'Pattern.negate()'" "false"
import java.util.function.Predicate;

public class Main {
  Predicate<Predicate<Object>> test() {
    return p -> !<caret>p.test(p);
  }
}

