// "Replace Optional.isPresent() condition with ifPresent()" "true"

import java.util.*;

public class Main {
  String myString;

  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      myString = str.get();
    }
  }
}