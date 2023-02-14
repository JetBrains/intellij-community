// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.util.Optional;

public class Main {
  public void test(Optional<String> opt) {
      opt.ifPresent(s -> {
          if (s.equals("abc"))
              System.out.println(s);
      });
  }
}
