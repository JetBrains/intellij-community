// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testConjunction() {
    List<Boolean> booleans = new ArrayList<>();
    boolean acc = booleans.stream().reduce(true, (a, b) -> a && b);
  }
}