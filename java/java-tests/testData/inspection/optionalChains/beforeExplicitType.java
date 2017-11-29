// "Fix all 'Simplify Optional call chains' problems in file" "false"
import java.util.Optional;

public class Tests {
  static <T> void test(Optional<? extends T> opt, T defaultT) {
    T t = opt.<T>map(x -> x).orEl<caret>se(defaultT);
  }
}
