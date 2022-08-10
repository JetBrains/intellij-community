// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
    if (str.isPre<caret>sent()) {
      return str.get();
    }
    return "";
  }
}