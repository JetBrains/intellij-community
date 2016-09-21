// "Replace Optional.isPresent() condition with map().orElse()" "true"

import java.util.*;

public class Main {
  public int testOptional2(Optional<List<String>> str) {
    if(str.isPrese<caret>nt()) {
      return str.get().size() > 5 ? 5 : str.get().size();
    }
    return 0;
  }
}