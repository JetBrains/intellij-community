// "Replace 'map()' with 'flatMap()'" "true"
import java.util.*;

class Test {
  native Optional<String> getOptional();

  void test(Optional<Test> opt) {
    opt.<caret>map/*0*/(a -> a.getOptional()/*1*/.orElse(/*2*/null)).ifPresent(System.out::println);
  }
}