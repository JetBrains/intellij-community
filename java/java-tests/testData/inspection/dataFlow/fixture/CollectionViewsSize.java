import java.util.*;

public class CollectionViewsSize {
  void test(List<String> input) {
    List<String> list1 = Collections.unmodifiableList(Arrays.asList("1", "2", "3"));
    if (<warning descr="Condition 'list1.size() == 3' is always 'true'">list1.size() == 3</warning>) {}
    List<String> list2 = Collections.synchronizedList(Collections.checkedList(input, String.class));
    if (<warning descr="Condition 'list2.size() == input.size()' is always 'true'">list2.size() == input.size()</warning>) {}
  }
}