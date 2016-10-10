// "Replace Optional.isPresent() condition with ifPresent()" "false"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      System.out.println(str);
    }
  }
}