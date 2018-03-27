import java.util.*;

class LessThanRelations {
  // IDEA-184278
  void m(int value) {
    for (int i = 0; i < value; i++) {
      if (<warning descr="Condition 'i < value' is always 'true'">i < value</warning>) System.out.println();
    }
  }

  void transitive(int a, int b, int c) {
    if(a > b && b >= c) {
      System.out.println("possible");
      if(<warning descr="Condition 'c == a' is always 'false'">c == a</warning>) {
        System.out.println("Impossible");
      }
      if(<warning descr="Condition 'c > a' is always 'false'">c > a</warning>) {
        System.out.println("Impossible");
      }
    }
  }

  void aioobe(Object[] arr, int pos) {
    if(pos >= arr.length) {
      System.out.println(arr[<warning descr="Array index is out of bounds">pos</warning>]);
    }
  }

  void wrongOrder(Object[] arr, Object e) {
    int index = 0;
    while(arr[index] != e && <warning descr="Condition 'index < arr.length' is always 'true' when reached">index < arr.length</warning>) {
      index++;
    }
    System.out.println(index);
  }

  void list(List<String> list, int index) {
    if(index >= list.size()) {
      System.out.println("Big index");
    } else if(index > 0 && <warning descr="Condition '!list.isEmpty()' is always 'true' when reached">!<warning descr="Condition 'list.isEmpty()' is always 'false' when reached">list.isEmpty()</warning></warning>) {
      System.out.println("ok");
    }
  }
}
