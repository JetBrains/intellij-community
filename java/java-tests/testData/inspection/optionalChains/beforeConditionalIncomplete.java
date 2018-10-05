// "Fix all 'Optional call chain can be simplified' problems in file" "false"
import java.util.Optional;

public class Test {
  String test(Optional<String> opt) {
    return opt.map(x -> x.isEmpty() ? null : ).or<caret>Else(null);
  }
}