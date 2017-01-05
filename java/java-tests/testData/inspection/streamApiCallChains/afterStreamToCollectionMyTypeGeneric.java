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
      new MyType<String, Number>(s).contains("abc");
  }
}