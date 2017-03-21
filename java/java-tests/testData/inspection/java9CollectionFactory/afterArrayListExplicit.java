// "Replace with 'List.of' call" "true"
import java.util.*;

public class Test {
  public void testList() {
    List<Integer> list;
      list = List.of(1, 2);
    System.out.println(list);
  }
}
