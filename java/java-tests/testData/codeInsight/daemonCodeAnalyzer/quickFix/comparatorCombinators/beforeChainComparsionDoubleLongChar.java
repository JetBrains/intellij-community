// "Replace with Comparator chain" "true"

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
    objs.sort((o1, o2) -> {<caret>
      int res = Double.compare(o1.first, o2.first);
      if(res == 0) res = Long.compare(o1.second, o2.second);
      if(res == 0) res = o1.third() - o2.third();
      if(res == 0) res = o1.fourth.compareTo(o2.fourth);
      return res;
    });
  }
}
