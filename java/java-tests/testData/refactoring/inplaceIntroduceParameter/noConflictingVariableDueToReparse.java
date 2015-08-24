import java.util.ArrayList;
import java.util.List;

class Test {

  public static List<String> example() {
    String fo<caret>o = "";
    List<String> list = new ArrayList<>();
    list.add(foo);
    return list;
  }
}
