// "Replace !Stream.anyMatch(...) with noneMatch(...)" "true-preview"

import java.util.*;

class Test {
  public boolean testAnyMatch(List<List<String>> data) {
    if(!data.stream().flatMap(Collection::stream).anyM<caret>atch(str -> str.isEmpty()))
      return true;
  }
}