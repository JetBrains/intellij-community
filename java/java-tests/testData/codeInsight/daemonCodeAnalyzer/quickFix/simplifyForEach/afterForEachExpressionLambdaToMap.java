// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private void test() {
    List<String> other = new ArrayList<>();
      HashMap<Integer, String> map = other.stream().collect(Collectors.toMap(String::length, s -> s, (a, b) -> a, HashMap::new));
  }
}
