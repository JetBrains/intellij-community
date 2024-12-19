import java.util.Optional;
import java.util.function.Predicate;

public class Test {
  public static void main(String[] args) {
    Predicate<Optional<?>> p = Optional::isPresent<caret>;
  }
}