// "Initialize variable 'msg'" "true-preview"

import java.util.Optional;

public class OptionalTest {
  public static void main(String[] args) {
    Optional<String> msg = Optional.empty();
    if (args.length > 5) {
      msg = Optional.of("hello");
    }
    System.out.println("msg = "+msg);
  }
}