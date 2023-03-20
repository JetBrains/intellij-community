// "Replace Optional presence condition with functional style expression" "false"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      str.get();
    }
  }
}