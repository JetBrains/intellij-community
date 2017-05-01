// "Simplify optional chain to '(String)...orElse(...)'" "true"
import java.util.Optional;

public class Test {
  public void test(Optional<Object> opt) {
    String result = (String) opt.filter(opt -> opt instanceof String).orElse(null);
  }
}