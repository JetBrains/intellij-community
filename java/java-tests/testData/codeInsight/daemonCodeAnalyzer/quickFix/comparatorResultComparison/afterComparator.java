// "Replace with '>= 0'" "true"
import java.util.*;

class Test {
  void test(Comparator<String> cmp) {
    if(cmp.compare("a", "b") >= 0) {
      System.out.println("Oops");
    }
  }
}