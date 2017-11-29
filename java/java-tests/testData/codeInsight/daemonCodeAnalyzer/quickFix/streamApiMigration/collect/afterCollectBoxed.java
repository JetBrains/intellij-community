// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void testPrimitiveMap(List<String> data) {
      List<Integer> list = data.stream().filter(str -> str.startsWith("xyz")).mapToInt(String::length).filter(len -> len > 10).boxed().collect(Collectors.toList());
  }
}