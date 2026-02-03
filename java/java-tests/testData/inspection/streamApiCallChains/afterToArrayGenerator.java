// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.*;
import java.util.function.*;

class Test {
  public void testToArray(List<String[]> data, IntFunction<String[]> generator) {
    Object[] array = data.toArray(generator);
  }
}