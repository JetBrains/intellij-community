// "Replace with 'Comparator' chain" "true-preview"

import java.util.List;

public class Main {
  static class Part implements Comparable<Part> {
  }

  static class Obj {
    Part first;
    Part second;
    int third;
  }

  void sort(List<Obj> objs) {
    objs.sort((o1, o2) -> {<caret>
      int res = o1.first.compareTo(o2.first);
      if(res == 0) res = o1.second.compareTo(o2.second);
      if(res == 0) res = o1.third - o2.third;
      return res;
    });
  }
}
