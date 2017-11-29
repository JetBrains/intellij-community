// "Replace with 'List.of' call" "false"
import java.util.*;

public class Test {
  public void testList() {
    List<Integer> list = new ArrayList<>();
    list.add(1);
    list.add(2);
    List<Integer> list2 = Collections.unmodifi<caret>ableList(list);
    System.out.println(list); // mutable list is reused here
  }
}
