// "Remove redundant steps from optional chain" "true"
import java.util.Optional;

public class Test {
  public Optional<String> test() {
    return getOpt().map(Optional::of).orEl<caret>se(Optional.empty());
  }

  private Optional<String> getOpt() {
    return Optional.of("foo");
  }
}