// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testConjunctionBoxed() {
    List<Boolean> booleans = new ArrayList<>();
    Boolean acc = booleans.stream().reduce(true, (a, b) -> a && b);
  }
}