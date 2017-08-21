// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  private void collect() {
      Set<String> res = Stream.<String>empty().filter(s -> !s.isEmpty()).collect(Collectors.toSet());
  }
}
