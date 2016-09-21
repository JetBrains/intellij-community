// "Replace Optional.isPresent() condition with orElse()" "true"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
      return str.orElse("");
  }
}