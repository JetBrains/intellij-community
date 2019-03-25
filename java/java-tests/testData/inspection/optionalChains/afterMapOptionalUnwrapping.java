// "Replace 'map()' with 'flatMap()'" "true"
import java.util.*;

class Test {
  native Optional<String> getOptional();

  void test(Optional<Test> opt) {
      /*0*/
      /*1*/
      /*2*/
      opt.flatMap(Test::getOptional).ifPresent(System.out::println);
  }
}