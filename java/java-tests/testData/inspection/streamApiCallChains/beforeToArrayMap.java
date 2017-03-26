// "Replace 'collection.stream().toArray()' with 'collection.toArray()'" "false"

import java.util.*;

class Test {
  public void testToArray(List<String> data) {
    Object[] array = data.stream().map(x -> x).to<caret>Array();
  }
}