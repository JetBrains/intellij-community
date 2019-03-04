// "Replace 'map()' with 'flatMap()'" "true"
import java.util.*;

class Test {
  native Optional<String> getOptional();

  void test(Optional<Test> opt) {
    opt.map<caret>(Test::getOptional).ifPresent(x -> x.ifPresent(System.out::println));
  }
}