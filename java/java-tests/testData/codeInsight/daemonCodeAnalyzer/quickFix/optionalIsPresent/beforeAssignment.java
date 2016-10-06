// "Replace Optional.isPresent() condition with orElse()" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
    if (str.isPrese<caret>nt()) {
      val = str.get();
    } else {
      val = "";
    }
    System.out.println(val);
  }
}