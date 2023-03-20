// "Replace Optional presence condition with functional style expression" "false"

import java.util.*;

public class Main {
  public int testOptionalRaw(Optional opt) {
    if(opt.isPre<caret>sent()) {
      return Math.abs(opt.get().hashCode());
    }
    return 0;
  }
}