// "Replace Optional presence condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import java.util.*;

public class Main {
  public void testOptional(Optional<String> str) {
    if (str == null) str = Optional.empty();
    if (str.isPrese<caret>nt()) {
      Object obj = str.get();
      use(obj);
    }
  }

  void use(Object obj) {
    System.out.println("Object");
  }

  void use(String obj, int i) {
    System.out.println("String");
  }
}
