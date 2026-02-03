// "Simplify optional chain to 'getOpt().map(...)'" "true"
import java.util.Optional;

public class Test {
  public Optional<String> test() {
    return getOpt().map(x -> Optional.ofNullable(x.trim())).orEl<caret>seGet(Optional::empty);
  }

  private Optional<String> getOpt() {
    return Optional.of("foo");
  }
}