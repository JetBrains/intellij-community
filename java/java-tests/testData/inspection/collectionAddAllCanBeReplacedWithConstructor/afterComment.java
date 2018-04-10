// "Replace 'addAll()' call with parametrized constructor call" "true"
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Test {
  void test(Object c) {
      //noinspection unchecked
      /*inside*/
      // after
      List<String> list = new ArrayList<>((/*cast!*/Collection<String>) c);
    System.out.println(list);
  }
}
