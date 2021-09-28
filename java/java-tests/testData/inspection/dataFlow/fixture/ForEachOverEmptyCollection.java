import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ForEachOverEmptyCollection {
  void testArrayAfter(String[] arr) {
    int count = 0;
    boolean hasItem = false;
    for(String str : arr) {
      if(str != null) {
        count++;
      }
      hasItem = true;
    }
    if(<warning descr="Condition 'arr.length == 0 && count > 0' is always 'false'">arr.length == 0 && <warning descr="Condition 'count > 0' is always 'false' when reached">count > 0</warning></warning>) {
      // count > 0 means we visited the loop -- impossible
      System.out.println("Impossible");
    }
    if(!hasItem) {
      // we never visited the loop: array is empty
      System.out.println(arr[<warning descr="Array index is out of bounds">1</warning>]);
    }
  }

  void testCollectionAfter(List<String> list) {
    boolean hasItem = false;
    String max = null;
    for (String s : list) {
      if(!hasItem || s.compareTo(max) > 0) {
        max = s;
      }
      hasItem = true;
    }
    if(!hasItem) {
      System.out.println(
        list.<warning descr="The call to 'get' always fails as an argument is out of bounds">get</warning>(<warning descr="Condition 'max == null' is always 'true'">max == null</warning> ? 0 : 1));
    }
  }
}
