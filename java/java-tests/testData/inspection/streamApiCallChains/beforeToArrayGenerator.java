// "Fix all 'Simplify stream API call chains' problems in file" "false"

import java.util.*;
import java.util.function.*;

class Test {
  public void testToArray(List<String[]> data, IntFunction<String[]> generator) {
    Object[] array = data.stream().toA<caret>rray(generator);
  }
}