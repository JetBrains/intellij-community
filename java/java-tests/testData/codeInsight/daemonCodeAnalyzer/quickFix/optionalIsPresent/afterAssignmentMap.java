// "Replace Optional.isPresent() condition with map().orElse()" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
      // line comment
// another line comment
//before trim
/* block comment *//*block comment*/
      val = str.map(String::trim).orElse("");
    System.out.println(val);
  }
}