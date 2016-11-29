// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  boolean find(List<String> data) {
    if(data != null) {
        return data.stream().map(String::trim).anyMatch(trimmed -> trimmed.startsWith("xyz"));
    } else {
      throw new IllegalArgumentException();
    }
  }
}
