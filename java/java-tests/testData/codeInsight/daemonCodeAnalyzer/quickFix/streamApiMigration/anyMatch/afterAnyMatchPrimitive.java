// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public boolean testPrimitiveMap(List<String> data) {
      return data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).anyMatch(len -> len > 10);
  }
}