// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public int testPrimitiveMap(List<String> data) {
      return data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len > 10).findFirst().orElse(0);
  }
}