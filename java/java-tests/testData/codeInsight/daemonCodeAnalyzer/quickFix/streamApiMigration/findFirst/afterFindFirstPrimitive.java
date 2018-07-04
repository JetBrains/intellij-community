// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public int testPrimitiveMap(List<String> data) {
      /*ten*/
      return data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len /*bigger*/ > 10).findFirst().orElse(0);
  }
}