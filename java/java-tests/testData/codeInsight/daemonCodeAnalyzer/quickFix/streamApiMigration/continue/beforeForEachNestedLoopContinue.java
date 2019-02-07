// "Replace with forEach" "true"

import java.util.Collection;

public class Test {
  void test(int[] arr, int[] arr2) {
    for<caret>(int x : arr) {
      int y = x*2;
      if(x > y) continue; // comment
      for(int i : arr2) {
        if (i % 2 == 0) continue;
        System.out.println(x);
      }
    }
  }
}
