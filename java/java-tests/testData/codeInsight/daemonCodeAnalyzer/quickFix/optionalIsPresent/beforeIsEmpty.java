// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
    if (str.isEmp<caret>ty()) {
      val = "";
    } else {
      val = str.get();
    }
    System.out.println(val);
  }
}