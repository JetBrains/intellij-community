import java.util.*;

class Test {

  List<String> getList(int x) {
      if (x == 0) {
          List<String> strings<caret> = new ArrayList<>();
          strings.add("0");
          return (strings);
      } else {
          return (Collections.emptyList());
      }
  }
}