// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
      val = str.orElse("");
    System.out.println(val);
  }
}