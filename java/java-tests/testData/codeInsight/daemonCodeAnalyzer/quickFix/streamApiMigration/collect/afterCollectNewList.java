// "Replace with collect" "true-preview"

import java.util.*;
import java.util.stream.Collectors;

public class Test {
  public static LinkedList<String> test(String[] args) {
      return Arrays.stream(args).filter(s -> !s.isEmpty()).distinct().sorted().collect(Collectors.toCollection(LinkedList::new));
  }
}
