// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  boolean find(List<String> data, boolean other, boolean third) {
      return data.stream().map(String::trim).anyMatch(trimmed -> trimmed.startsWith("xyz")) || (other || third);
  }
}
