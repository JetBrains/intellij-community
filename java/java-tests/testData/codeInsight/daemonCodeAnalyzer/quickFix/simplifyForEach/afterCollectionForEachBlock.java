// "Replace with 'collect()'" "true-preview"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private void test(List<String> strs) {
    List<String> other = strs.stream().filter(s -> s.length() > 2).collect(Collectors.toList());
  }
}
