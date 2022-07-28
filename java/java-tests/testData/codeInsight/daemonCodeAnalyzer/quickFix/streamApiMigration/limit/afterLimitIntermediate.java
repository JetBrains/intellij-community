// "Replace with collect" "true-preview"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public Set<String> test(String[] array) {
      /*initial count*/
      Set<String> set = Arrays.stream(array).filter(Objects::nonNull).limit(10).collect(Collectors.toSet());
      return set;
  }
}