// "Replace 'collection.stream().toArray()' with 'collection.toArray()'" "false"

import java.util.*;
import java.util.function.*;

class Test {
  public void testToArray(List<String[]> data, IntFunction<String[]> generator) {
    Object[] array = data.stream().toA<caret>rray(generator);
  }
}