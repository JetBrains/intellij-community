// "Replace with forEach" "true"

import java.util.*;

public class Test {
  void test(Collection<String> obj) {
    for(String str : <caret>obj) {
      List// declaration
        <String> s2 = Collections.singleton(str);
      System.out.println(str+s2);
    }
  }
}
