// "Replace with 'collect()'" "true-preview"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private void test(List<String> other) {
    List<String> strs = other.stream().collect(Collectors.toList());
  }
}
