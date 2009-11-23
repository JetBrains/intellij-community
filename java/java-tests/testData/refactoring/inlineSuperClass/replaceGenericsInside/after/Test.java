import java.util.List;
import java.util.Set;

class Test {
    List<String> l;

    Test() {
      Set<String> s = new HashSet<String>();
      for (String t : s) {
        System.out.println(t);
      }
    }

    void foo(String t) {
      System.out.println(t);
    }

    void bar() {
      for (String t : l) {
        System.out.println(t);
      }
    }
}