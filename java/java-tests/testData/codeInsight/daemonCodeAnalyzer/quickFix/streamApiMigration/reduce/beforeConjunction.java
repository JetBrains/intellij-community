// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testConjunction() {
    List<Boolean> booleans = new ArrayList<>();
    boolean acc = true;
    for <caret> (Boolean bool : booleans) {
      acc &= bool;
    }
  }
}