// "Replace Optional.isPresent() condition with functional style expression" "false"

import java.util.Optional;

public class Main {
  void assignVariable(Optional<Object> opt) {
    String s;
    if(opt.isPre<caret>sent()) {
      s = opt.get().toString();
    }
    String s2 = s;
  }
}
