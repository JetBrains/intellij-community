// "Fix all 'Simplify Optional call chains' problems in file" "false"
import java.util.Optional;

public class Test {
  public String getDefaultValue() {
    return "foo";
  }

  public void test(Optional<String> opt) {
    // proposed change is longer:
    //              opt.filter(obj -> !obj.isEmpty()).map(obj -> obj.trim()).orElse(null);
    String result = opt.map(obj -> obj.isEmpty() ? null : obj.trim()).orEl<caret>se(null);
  }
}