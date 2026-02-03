// "Replace !IntStream.noneMatch(...) with anyMatch(...)" "true-preview"

import java.util.stream.*;

class Test {
  public boolean testNoneMatch(int[] data) {
    if(!IntStream.of(data).noneM<caret>atch(i -> i > 0))
      return true;
  }
}