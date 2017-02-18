// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  String val;

  public void testOptional(Optional<String> str) {
      this.val = str.orElse("");
    System.out.println(val);
  }
}