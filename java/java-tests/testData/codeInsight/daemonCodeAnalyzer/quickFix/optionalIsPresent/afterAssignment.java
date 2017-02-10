// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
      val = str.orElse("");
    System.out.println(val);
  }
}