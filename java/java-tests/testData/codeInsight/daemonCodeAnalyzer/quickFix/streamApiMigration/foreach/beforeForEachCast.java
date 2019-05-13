// "Replace with forEach" "true"

import java.util.Collection;

public class Test {
  void test(Object obj) {
    for(String str : (Collection<String>)<caret>obj) {
      System.out.println(str);
    }
  }
}
