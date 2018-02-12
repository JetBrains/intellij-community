// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Test {
  void test(Object c) {
    List<String> other, list;
    //noinspection unchecked
    list = new ArrayList<>((Collection<String>) c);
    System.out.println(list);
  }
}
