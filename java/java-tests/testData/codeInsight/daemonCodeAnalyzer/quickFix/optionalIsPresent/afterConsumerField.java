// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
  String myString;

  public void testOptional(Optional<String> str) {
      str.ifPresent(s -> myString = s);
  }
}