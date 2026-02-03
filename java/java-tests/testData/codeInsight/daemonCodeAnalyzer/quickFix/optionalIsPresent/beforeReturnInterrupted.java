// "Replace Optional presence condition with functional style expression" "false"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
    if (str.isPre<caret>sent()) {
      return str.get().trim();
    }
    System.out.println("oops");
    return "";
  }
}