// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testConjunctionBoxed() {
    List<Boolean> booleans = new ArrayList<>();
    Boolean acc = true;
    for <caret> (Boolean bool : booleans) {
      acc &= bool;
    }
  }
}