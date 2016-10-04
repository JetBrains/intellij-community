// "Replace !Stream.anyMatch(...) with Stream.noneMatch(...)" "true"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(data.stream().flatMap(Collection::stream).noneMatch(str -> str.isEmpty()))
      return true;
  }
}