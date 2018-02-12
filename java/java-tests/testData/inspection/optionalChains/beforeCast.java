// "Simplify optional chain to '(String)...orElse(...)'" "true"
import java.util.Optional;

public class Test {
  public void test(Optional<Object> opt) {
    String result = opt.filter(opt -> opt instanceof String).map(opt -> (String) opt).or<caret>Else(null);
  }
}