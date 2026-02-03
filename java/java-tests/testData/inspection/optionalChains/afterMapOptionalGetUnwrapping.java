// "Replace 'map()' with 'flatMap()'" "true"
import java.util.*;

class Test {
  void test() {
    Optional<Test> opt = Optional.of(new Test());
      /*0*/
      /*1*/
      opt.flatMap(a -> Optional.of("foo")).ifPresent(System.out::println);
  }
}