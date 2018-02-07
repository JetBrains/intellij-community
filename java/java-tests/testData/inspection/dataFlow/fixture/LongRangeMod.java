import java.util.*;

public class LongRangeMod {
  void test(int[] arr, int x) {
    if(<warning descr="Condition 'arr.length % x < 0' is always 'false'">arr.length % x < 0</warning>) {
      System.out.println("Impossible");
    }
  }

  void test(int x) {
    for (int i = 0; i < 10; i++) {
      if(<warning descr="Condition 'i % x > 10' is always 'false'">i % x > 10</warning>) {
        System.out.println("impossible");
      }
    }
  }

  // IDEA-113410
  void test() {
    List<String> someList = new LinkedList<>();

    int i = 0;
    Object something = null;
    for (String s : someList) {
      if(i % 2 == 0) {
        something = new Object();
      }
      else {
        something.toString(); // <== No warning here:
        // now we know that i = 0 on the first iteration, thus
        // i % 2 == 0 is true, thus something always points to an Object after first iteration
      }
      i++;
    }
  }
}
