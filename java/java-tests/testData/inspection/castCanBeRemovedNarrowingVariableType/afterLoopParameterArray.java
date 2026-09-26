// "Change type of 'e' to 'String' and remove cast" "true"
import java.util.*;

class Test {
  void test(String[] list) {
    for(String e : list) {
      System.out.println(e.trim());
    }
  }
}