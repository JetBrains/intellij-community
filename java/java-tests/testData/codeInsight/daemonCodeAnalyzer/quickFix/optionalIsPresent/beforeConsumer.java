// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      System.out.println(str.get());
    }
  }
}