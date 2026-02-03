import java.util.*;

public class LongRangeDiff {
  void test(int x, int y) {
    if (x == y && <warning descr="Condition 'x - y == 0' is always 'true' when reached">x - y == 0</warning>) { }
    
    if (<warning descr="Condition 'x != y && x - y == 0' is always 'false'">x != y && <warning descr="Condition 'x - y == 0' is always 'false' when reached">x - y == 0</warning></warning>) {}
  }
  
  void test2(int[] arr1, int[] arr2) {
    if(arr1.length > arr2.length) {
      int diff1 = arr1.length - arr2.length;
      int diff2 = arr2.length - arr1.length;
      if (<warning descr="Condition 'diff1 <= 0' is always 'false'">diff1 <= 0</warning>) {}
      if (<warning descr="Condition 'diff2 >= 0' is always 'false'">diff2 >= 0</warning>) {}
    }
  }
}
