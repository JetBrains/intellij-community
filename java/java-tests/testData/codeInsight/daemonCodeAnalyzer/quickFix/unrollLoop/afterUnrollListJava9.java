// "Unroll loop" "true"
import java.util.*;

class Test {
  void test() {
      if (!"foo".isEmpty()) {
          System.out.println("foo");
      }
      if (!"bar".isEmpty()) {
          System.out.println("bar");
      }
      if (!"baz".isEmpty()) {
          System.out.println("baz");
      }
      if (!"".isEmpty()) {
          System.out.println("");
      }
  }
}