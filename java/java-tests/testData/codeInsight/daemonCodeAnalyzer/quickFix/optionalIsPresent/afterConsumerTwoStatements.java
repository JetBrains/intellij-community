// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
      str.ifPresent(s -> {
          System.out.println(s);
          // once again!
          System.out.println(s);
      });
  }
}