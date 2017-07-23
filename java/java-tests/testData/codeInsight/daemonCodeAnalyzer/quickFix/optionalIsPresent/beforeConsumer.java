// "Replace Optional.isPresent() condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str == null) str = Optional.empty();
    if (str.isPrese<caret>nt()) {
      System.out.println(str.get());
    }
  }
}