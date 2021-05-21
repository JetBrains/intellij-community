// "Change type of 'e' to 'String' and remove cast" "true"
import java.util.*;

class Test {
  void test(String[] list) {
    for(Object e : list) {
      System.out.println(((<caret>String)e).trim());
    }
  }
}