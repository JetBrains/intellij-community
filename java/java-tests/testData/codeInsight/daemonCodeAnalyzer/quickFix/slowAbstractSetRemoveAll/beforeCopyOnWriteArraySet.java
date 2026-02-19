// "Fix all 'Call to 'set.removeAll(list)' may work slowly' problems in file" "false"

import java.util.*;
import java.util.concurrent.*;

class Test {
  void foo(CopyOnWriteArraySet<Integer> source, List<Integer> removals) {
    source.removeAll<caret>(removals);
  }
}
