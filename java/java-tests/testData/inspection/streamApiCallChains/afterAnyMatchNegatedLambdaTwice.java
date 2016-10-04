// "Replace !Stream.anyMatch(x -> !(...)) with Stream.allMatch(...)" "true"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(data.stream().flatMap(Collection::stream).allMatch(str -> str.isEmpty()))
      return true;
  }
}