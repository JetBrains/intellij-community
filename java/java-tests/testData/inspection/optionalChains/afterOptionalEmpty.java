// "Remove redundant steps from optional chain" "true"
import java.util.Optional;

public class Test {
  public Optional<String> test() {
    return getOpt();
  }

  private Optional<String> getOpt() {
    return Optional.of("foo");
  }
}