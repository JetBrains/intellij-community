// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
    if (str.isPrese<caret>nt()) {
      val = str.get().trim();
    } else {
      val = getDefault();
    }
    System.out.println(val);
  }

  public String getDefault() {
    return "";
  }
}