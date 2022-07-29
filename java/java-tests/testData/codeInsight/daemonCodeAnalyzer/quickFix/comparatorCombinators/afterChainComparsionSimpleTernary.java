// "Replace with 'Comparator' chain" "true-preview"

import java.util.Comparator;
import java.util.List;

public class Main {
  static class Part implements Comparable<Part> {
  }

  static class Obj {
    Part first;
    Part second;
  }

  void sort(List<Obj> objs) {
    objs.sort(Comparator.comparing((Obj o) -> o.first).thenComparing(o -> o.second));
  }
}
