// "Remove folded 'ifPresent()' call" "true"
import java.util.*;

class Test {
  native String getNonOptional();

  static native Optional<String> getOptional(Test v);

  void test(Optional<Test> opt) {
    opt.map(Test::getNonOptional).flatMap(x -> getOptional(x)).ifPresent(System.out::println);
  }
}