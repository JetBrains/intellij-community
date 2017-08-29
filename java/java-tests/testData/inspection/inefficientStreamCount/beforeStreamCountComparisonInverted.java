// "Replace Stream().filter().count() > 0 with stream.anyMatch()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return 0 < Arrays.asList('ds', 'e', 'fe').stream().filter(s -> s.length() > 1).c<caret>ount();
  }
}