// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Test {
  MyObj test(List<String> list) {
    List<String> res = list.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
      return new MyObj(res);
  }

  private class MyObj {
    public MyObj(List<String> res) {
    }
  }
}
