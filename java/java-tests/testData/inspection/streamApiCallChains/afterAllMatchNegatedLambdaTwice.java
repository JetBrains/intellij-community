// "Replace !Stream.allMatch(x -> !(...)) with anyMatch(...)" "true-preview"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(data.stream().flatMap(Collection::stream).anyMatch(str -> str.isEmpty()))
      return true;
  }
}