// "Fix all 'Optional call chain can be simplified' problems in file" "false"
import java.util.Optional;

public class Tests {
  static void test(Optional<Double> opt, Integer defaultT) {
    Number t = opt.<Number>map(x -> x).orEl<caret>se(defaultT);
  }
}
