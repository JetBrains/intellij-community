// "Replace with 'Test.MyType' constructor" "false"

import java.util.*;
import java.util.stream.*;

class Test {
  static class MyType extends ArrayList<String> {

  }

  public static void test(List<String> s) {
    s.str<caret>eam().collect(Collectors.toCollection(MyType::new)).contains("abc");
  }
}