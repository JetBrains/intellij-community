// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testDisjunction() {
    List<Boolean> booleans = new ArrayList<>();
      boolean acc = booleans.stream().reduce(false, (a, b) -> a || b);
  }
}