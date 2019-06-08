// "Replace 'map()' with 'flatMap()'" "true"
import java.util.*;

class Test {
  void test() {
    Optional<Test> opt = Optional.of(new Test());
    opt.<caret>map/*0*/(a -> Optional.of("foo")/*1*/.get()).ifPresent(System.out::println);
  }
}