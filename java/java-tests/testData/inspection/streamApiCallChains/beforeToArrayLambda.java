// "Replace 'collection.stream().toArray()' with 'collection.toArray()'" "true"

import java.util.*;

class Test {
  public void testToArray(List<String[]> data) {
    String[][] array = data.subList(0, /*max number*/ 10).stream().to<caret>Array((size) -> /*generate array*/ new String[(size)][]);
  }
}