// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Test {
  void test(Object c) {
    List<String> list = new ArrayList<>();
    //noinspection unchecked
    list./*inside*/ad<caret>dAll((/*cast!*/Collection<String>) c); // after
    System.out.println(list);
  }
}
