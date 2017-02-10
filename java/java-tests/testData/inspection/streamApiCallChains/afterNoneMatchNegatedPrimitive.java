// "Replace !IntStream.noneMatch(...) with anyMatch(...)" "true"

import java.util.stream.*;

class Test {
  public boolean testNoneMatch(int[] data) {
    if(IntStream.of(data).anyMatch(i -> i > 0))
      return true;
  }
}