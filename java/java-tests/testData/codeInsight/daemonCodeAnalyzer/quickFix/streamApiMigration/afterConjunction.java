// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testConjunction() {
    List<Boolean> booleans = new ArrayList<>();
      boolean acc = booleans.stream().map(bool -> bool).reduce(true, (a, b) -> a && b);
  }
}