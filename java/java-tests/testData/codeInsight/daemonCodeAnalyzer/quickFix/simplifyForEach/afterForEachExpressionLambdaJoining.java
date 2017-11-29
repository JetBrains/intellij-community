// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
      String sb = strs.stream().collect(Collectors.joining());
  }
}
