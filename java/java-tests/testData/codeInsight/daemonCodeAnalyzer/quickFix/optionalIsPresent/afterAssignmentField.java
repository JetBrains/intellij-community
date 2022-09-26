// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  String val;

  public void testOptional(Optional<String> str) {
      this.val = str.orElse("");
    System.out.println(val);
  }
}