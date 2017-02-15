// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test {
  public List<String> test(String[] data) {
    int top = Math.min(10, data.length);
      List<String> result = Arrays.stream(data).limit(10).map(String::trim).filter(item -> !item.isEmpty()).collect(Collectors.toList());
      return result;
  }
}
