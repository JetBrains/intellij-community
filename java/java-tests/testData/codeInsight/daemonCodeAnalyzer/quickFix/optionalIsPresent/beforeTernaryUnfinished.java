// "Replace Optional.isPresent() condition with functional style expression" "false"

import java.util.Optional;

public class Main {
  public void test(Optional<String> opt) {
    System.out.println(opt.<caret>isPresent() ? opt.get():);
  }
}