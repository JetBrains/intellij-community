// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public static Runnable get(Optional<String> s) {
      return s.<Runnable>map(string -> string::trim).orElse(null);
  }
}