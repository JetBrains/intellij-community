// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  List<String> getStrings(List<?> list) {
    List<String> result = list.stream().filter(o -> o instanceof String).map(o -> (String) o).collect(Collectors.toList());
      return result;
  }
}