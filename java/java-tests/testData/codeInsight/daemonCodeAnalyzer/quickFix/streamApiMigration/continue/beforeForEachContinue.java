// "Replace with forEach" "true-preview"

import java.util.Collection;

public class Test {
  void test(int[] arr) {
    for<caret>(int x : arr) {
      int y = x*2;
      if(x > y) continue; // comment
      System.out.println(x);
    }
  }
}
