// "Replace with collect" "true"

import java.util.*;

public class Test {
  MyObj test(List<String> list) {
    List<String> res = new ArrayList<>();
    for (String s : li<caret>st) {
      if(!s.isEmpty()) {
        res.add(s);
      }
    }
    return new MyObj(res);
  }

  private class MyObj {
    public MyObj(List<String> res) {
    }
  }
}
