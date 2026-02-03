// "Simplify optional chain to 'opt.filter(...).map(...).orElseGet(...)'" "true"
import java.util.Optional;

public class Test {
  public String getDefaultValue() {
    return "foo";
  }

  public void test(Optional<String> opt) {
    String result = opt.filter(obj -> !obj.isEmpty()).map(String::trim).orElseGet(this::getDefaultValue);
  }
}