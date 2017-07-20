// "Replace with reduce()" "true"

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