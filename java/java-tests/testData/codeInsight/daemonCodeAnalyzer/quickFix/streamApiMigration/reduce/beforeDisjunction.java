// "Replace with reduce()" "true-preview"

import java.util.*;

public class Main {
  public void testDisjunction() {
    List<Boolean> booleans = new ArrayList<>();
    boolean acc = false;
    for <caret> (Boolean bool : booleans) {
      acc |= bool;
    }
  }
}