// "Replace Optional presence condition with functional style expression" "INFORMATION"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      System.out.println(str.get());
      // once again!
      System.out.println(str.get());
    }
  }
}