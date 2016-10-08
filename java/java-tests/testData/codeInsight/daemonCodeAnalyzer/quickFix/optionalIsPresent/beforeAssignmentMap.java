// "Replace Optional.isPresent() condition with map().orElse()" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
    if (str.isPrese<caret>nt()) {
      val = // line comment
      // another line comment
        str.get()//before trim
          .trim() /* block comment *//*block comment*/;
    } else {
      val = "";
    }
    System.out.println(val);
  }
}