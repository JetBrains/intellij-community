// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.*;

public class Main {
  public int testOptional2(Optional<List<String>> str) {
      return str.map(strings -> strings.size() > 5 ? 5 : strings.size()).orElse(0);
  }
}