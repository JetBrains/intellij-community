import java.util.*;

class Test {

  void test() {
    Map.Entry<?, ?>[] e = {Map.entry("foo", "bar")};
    System.out.println(Map.ofEntries<caret>(e[0]));
  }

}