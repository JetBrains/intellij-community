// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  public static Runnable get(Optional<String> s) {
    if(s.isPres<caret>ent()) {
      return s.get()::trim;
    }
    return null;
  }
}