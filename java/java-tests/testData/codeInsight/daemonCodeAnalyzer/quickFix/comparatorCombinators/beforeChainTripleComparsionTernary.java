// "Replace with Comparator chain" "true"

import java.util.List;

public class Main {
  static class Part implements Comparable<Part> {
  }

  static class Obj {
    Part first;
    Part second;
    Part third;
  }

  void sort(List<Obj> objs) {
    objs.sort((o1, o2) -> {<caret>
      int res = o1.first.compareTo(o2.first);
      if(res != 0) return res;
      res = o1.second.compareTo(o2.second);
      return res == 0 ? o1.third.compareTo(o2.third) : res;
    });
  }
}
