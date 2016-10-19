// "Replace with 'Test.MyType' constructor" "true"

import java.util.*;
import java.util.stream.*;

class Test {
  static class MyType<A,B> extends ArrayList<String> {
    public MyType() {}

    public MyType(Collection<String> coll) {
      super(coll);
    }
  }

  public static void testMy(List<String> s) {
    s.stream().collect(Collectors.toCollection(MyType<caret><String, Number>::new)).contains("abc");
  }
}