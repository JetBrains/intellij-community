// "Replace 'stream().forEach()' with 'ifPresent()'" "true-preview"

import java.util.Optional;

public class Main {
  public void test(Optional<String> opt) {
    opt.ifPresent(System.out::println);
  }
}