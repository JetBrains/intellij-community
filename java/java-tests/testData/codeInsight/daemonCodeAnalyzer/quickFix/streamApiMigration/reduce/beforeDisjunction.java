// "Replace with reduce()" "true"

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