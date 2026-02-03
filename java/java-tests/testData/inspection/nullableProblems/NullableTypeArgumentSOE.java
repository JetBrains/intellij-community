import typeUse.*;
import java.util.*;

import java.util.Map;

class Test {
  interface MyMap extends Map<Integer, MyMap> {}

  void test(MyMap m) {
    Map<@Nullable Integer, @Nullable MyMap> test = m;
  }
}