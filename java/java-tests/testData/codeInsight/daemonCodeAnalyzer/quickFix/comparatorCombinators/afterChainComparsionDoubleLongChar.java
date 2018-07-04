// "Replace with Comparator chain" "true"

import java.util.Comparator;
import java.util.List;

public class Main {
  static class Part implements Comparable<Part> {
  }

  static class Obj {
    double first;
    long second;
    char third(){};
    Part fourth;
  }

  void sort(List<Obj> objs) {
    objs.sort(Comparator.comparingDouble((Obj o) -> o.first).thenComparingLong(o -> o.second).thenComparingInt(Obj::third).thenComparing(o -> o.fourth));
  }
}
