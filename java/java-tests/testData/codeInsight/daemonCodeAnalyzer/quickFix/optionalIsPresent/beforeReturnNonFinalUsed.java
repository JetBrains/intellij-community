// "Replace Optional presence condition with functional style expression" "false"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
    int i = 5;
    if (Math.random() > 0.5) i = 6;
    if (str.isPre<caret>sent()) {
      return str.get().substring(i);
    }
    return "";
  }
}