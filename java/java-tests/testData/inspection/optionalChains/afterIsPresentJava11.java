// "Replace with 'isEmpty()'" "true"
import java.util.Optional;

public class Test {
  void test(Optional<String> opt) {
    if(opt.isEmpty()) return;
  }
}