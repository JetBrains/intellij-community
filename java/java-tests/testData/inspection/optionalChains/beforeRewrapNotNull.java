// "Unwrap" "false"
import java.util.*;

public class Tests {
  private Optional<String> test(Optional<String> testOptional, String defaultVal) {
    return Optional.ofNullable(testOptional.or<caret>Else(defaultVal));
  }
}
