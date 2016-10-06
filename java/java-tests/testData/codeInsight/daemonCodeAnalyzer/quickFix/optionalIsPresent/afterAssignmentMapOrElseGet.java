// "Replace Optional.isPresent() condition with map().orElseGet()" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    String val;
      val = str.map(String::trim).orElseGet(this::getDefault);
    System.out.println(val);
  }

  public String getDefault() {
    return "";
  }
}