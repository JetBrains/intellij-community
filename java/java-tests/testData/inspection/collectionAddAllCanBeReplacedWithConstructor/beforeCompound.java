// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Test {
  void test(Object c) {
    List<String> list = new ArrayList<>(), other;
    //noinspection unchecked
    list.ad<caret>dAll((Collection<String>) c);
    System.out.println(list);
  }
}
