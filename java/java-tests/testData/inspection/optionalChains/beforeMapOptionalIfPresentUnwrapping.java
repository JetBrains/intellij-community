// "Remove folded 'ifPresent()' call" "true"
import java.util.*;

class Test {
  native Optional<String> getOptional();

  void test(Optional<Test> opt) {
    opt.map(Test::getOptional).<caret>ifPresent(x -> x.ifPresent(System.out::println));
  }
}