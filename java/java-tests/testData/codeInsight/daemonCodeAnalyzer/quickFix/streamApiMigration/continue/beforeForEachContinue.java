// "Replace with forEach" "true"

import java.util.Collection;

public class Test {
  void test(int[] arr) {
    for<caret>(int x : arr) {
      int y = x*2;
      if(x > y) continue;
      System.out.println(x);
    }
  }
}
