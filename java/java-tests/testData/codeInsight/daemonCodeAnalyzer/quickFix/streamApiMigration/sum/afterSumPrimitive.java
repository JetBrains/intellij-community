// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testPrimitiveMap(List<String> data) {
      int sum = data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len > 10).map(len -> len * 2).sum();
  }
}