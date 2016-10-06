// "Replace Optional.isPresent() condition with map().orElse()" "false"

import java.util.*;

public class Main {
  public int testOptionalRaw(Optional opt) {
    if(opt.isPre<caret>sent()) {
      return Math.abs(opt.get().hashCode());
    }
    return 0;
  }
}