// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  String val;

  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      this.val = str.get();
    } else {
      this.val = "";
    }
    System.out.println(val);
  }
}