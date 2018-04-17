import java.util.*;
import org.jetbrains.annotations.*;

class ForeachCollectionElement {
  void test() {
    int[] arr = new int [] {10,20,30,40,50,60,70,80};
    for(int i : arr) {
      if(<warning descr="Condition 'i == 75' is always 'false'">i == 75</warning>) {
        System.out.println("Impossible");
      }
    }
  }

  void test2() {
    for(int i : new int [] {10,20,30,40,50,60,70,80}) {
      if(<warning descr="Condition 'i > 71 && i < 79' is always 'false'">i > 71 && <warning descr="Condition 'i < 79' is always 'false' when reached">i < 79</warning></warning>) {
        System.out.println("Impossible");
      }
    }
  }

  void test3() {
    for(String s : Arrays.asList("foo", "bar", "baz")) {
      if(<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {
        System.out.println("impossible");
      }
    }
  }
}
