// "Replace Stream.allMatch(x -> !(...)) with Stream.noneMatch(...)" "true"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(data.stream().flatMap(Collection::stream).allM<caret>atch(str -> !str.isEmpty()))
      return true;
  }
}