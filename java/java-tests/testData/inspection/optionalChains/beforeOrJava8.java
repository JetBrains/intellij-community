// "Simplify optional chain to 'getOpt().or(...)'" "false"
import java.util.Optional;

public class Test {
  public Optional<String> test() {
    // no simpler alternative in Java 8
    return getOpt().map(Optional::of).orEl<caret>seGet(this::getOpt2);
  }

  private Optional<String> getOpt() {
    return Optional.of("foo");
  }

  private Optional<String> getOpt2() {
    return Optional.of("bar");
  }
}