// "Replace with '> 0'" "true"
import java.util.*;

class Test {
  void test(String str) {
    if(str.compareTo("xyz") =<caret>= 1) {
      System.out.println("Oops");
    }
  }
}