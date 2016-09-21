// "Replace Optional.isPresent() condition with ifPresent()" "true"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
      str.ifPresent(System.out::println);
  }
}