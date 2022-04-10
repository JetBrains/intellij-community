// "Fix all 'Call to 'list.containsAll(collection)' may have poor performance' problems in file" "true"
import java.util.Collection;
import java.util.List;

class Test {
  void check(List<String> list1, List<String> list2, Collection<String> collection) {
    list1.containsAll<caret>(collection);
    (((list1))).containsAll(collection);
    ((((Math.random() > 0.5 ? list1 : list2)))).containsAll(collection);
  }
}