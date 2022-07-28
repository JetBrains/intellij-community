// "Replace with reduce()" "true-preview"

import java.util.*;

public class Main {
  public void testDisjunction() {
    List<Boolean> booleans = new ArrayList<>();
    boolean acc = booleans.stream().reduce(false, (a, b) -> a || b);
  }
}