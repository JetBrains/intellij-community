// "Simplify optional chain to 'getOpt().or(...)'" "true"
import java.util.Optional;

public class Test {
  public Optional<String> test() {
    return getOpt().or(this::getOpt2);
  }

  private Optional<String> getOpt() {
    return Optional.of("foo");
  }

  private Optional<String> getOpt2() {
    return Optional.of("bar");
  }
}