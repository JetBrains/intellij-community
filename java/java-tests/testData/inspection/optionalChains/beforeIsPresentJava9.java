// "Replace with 'isEmpty()'" "false"
import java.util.Optional;

public class Test {
  void test(Optional<String> opt) {
    if(!opt.is<caret>Present()) return;
  }
}