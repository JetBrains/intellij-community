// "Replace with 'java.util.ArrayList' constructor" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.*;

class Test {
  public static void test(List<String> s) {
      new ArrayList<>(s).contains("abc");
  }
}