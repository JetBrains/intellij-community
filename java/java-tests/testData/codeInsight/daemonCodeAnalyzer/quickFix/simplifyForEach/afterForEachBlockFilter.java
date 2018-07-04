// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private void test() {
    List<String> strs;
    List<String> other = new ArrayList<>();
      strs = other.stream().filter(s -> s.length() > 2).collect(Collectors.toList());
  }
}
