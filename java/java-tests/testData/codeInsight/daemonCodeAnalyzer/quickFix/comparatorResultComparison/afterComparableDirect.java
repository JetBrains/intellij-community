// "Replace with '> 0'" "true-preview"
import java.util.*;

class Test {
  void test(String str) {
    if(str.compareTo("xyz") > 0) {
      System.out.println("Oops");
    }
  }
}