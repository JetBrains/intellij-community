// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
    if ((!(str.isPre<caret>sent()))) {
      return "";
    }
    return str.get().trim();
  }
}