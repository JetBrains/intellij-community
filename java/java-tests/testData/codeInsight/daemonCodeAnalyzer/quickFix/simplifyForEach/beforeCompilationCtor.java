// "Avoid mutation using Stream API 'count()' operation" "false"

import java.util.*;

public class Main {
  // EA-107550 - NPE: SimplifyForEachInspection.extractLambdaFromForEach
  void test() {
    int x = 0;
    class X {X(Runnable r) {}}
    new X(() -> <caret>x++);
  }
}
