// "Replace Optional.isPresent() condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import java.util.*;

public class Main {
  String myString;

  public void testOptional(Optional<String> str) {
    if (str.isPrese<caret>nt()) {
      myString = str.get().isEmpty() ? null : str.get();
    }
  }
}