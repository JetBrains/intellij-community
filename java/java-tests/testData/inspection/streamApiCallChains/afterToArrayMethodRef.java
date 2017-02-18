// "Replace 'collection.stream().toArray()' with 'collection.toArray()'" "true"

import java.util.*;

class Test {
  public void testToArray(List<String> data) {
    Object[] array = data.toArray(new String[0]);
  }
}