// "Replace 'stream().forEach()' with 'ifPresent()'" "true"

import java.util.Optional;

public class Main {
  public void test(Optional<String> opt) {
    opt.stream().for<caret>Each(System.out::println);
  }
}