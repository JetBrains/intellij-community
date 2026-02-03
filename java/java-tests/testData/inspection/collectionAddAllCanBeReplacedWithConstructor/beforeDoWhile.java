// "Replace 'addAll()' call with parametrized constructor call" "false"
import java.util.*;

class Test {
  List<String> test(String[][] data) {
    int i = data.length - 1;
    List<String> list = new ArrayList<>();
    do {
      list.a<caret>ddAll(Arrays.asList(data[i]));
    } while(--i > 0);
    return list;
  }
}
