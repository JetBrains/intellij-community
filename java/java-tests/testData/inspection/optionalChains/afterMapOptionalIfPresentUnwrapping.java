// "Remove folded 'ifPresent()' call" "true"
import java.util.*;

class Test {
  native Optional<String> getOptional();

  void test(Optional<Test> opt) {
    opt.map(Test::getOptional).flatMap(Test::getOptional).ifPresent(System.out::println);
  }
}