// "Replace with 'List.of' call" "true"
import java.util.*;

public class Test {
  public void testList() {
    List<Integer> list = new ArrayList<>();
    list.add(1);
    list.add(2);
    list = Collections.unmodifi<caret>ableList(list);
    System.out.println(list);
  }
}
