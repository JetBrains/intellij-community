// "Replace Stream.allMatch(x -> !(...)) with noneMatch(...)" "true-preview"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(data.stream().flatMap(Collection::stream).noneMatch(str -> str.isEmpty()))
      return true;
  }
}