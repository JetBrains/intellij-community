// "Replace !LongStream.anyMatch(...) with noneMatch(...)" "true-preview"

import java.util.stream.*;

class Test {
  public boolean testAnyMatch(long[] data) {
    if(!LongStream.of(data).anyM<caret>atch(i -> i > 0))
      return true;
  }
}