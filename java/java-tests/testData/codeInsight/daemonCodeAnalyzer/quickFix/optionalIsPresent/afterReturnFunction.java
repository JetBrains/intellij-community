// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  public static Runnable get(Optional<String> s) {
      return s.<Runnable>map(value -> value::trim).orElse(null);
  }
}