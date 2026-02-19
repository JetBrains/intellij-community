// "Replace 'stream().toArray()' with 'toArray()'" "true-preview"

import java.util.*;

class Test {
  public void testToArray(List<String[]> data) {
    String[][] array = data.subList(0, /*max number*/ 10).toArray((size) -> /*generate array*/ new String[(size)][]);
  }
}