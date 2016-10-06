// "Replace Optional.isPresent() condition with map().orElse()" "true"

import java.util.*;

public class Main {
  public String testOptional(Optional<String> str) {
      return str.map(String::trim).orElse("");
  }
}