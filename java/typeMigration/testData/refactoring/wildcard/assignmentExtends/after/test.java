import java.util.*;

class Test {
  void method(ArrayList<? extends Integer> p) {
       p[0] = new Integer(0);
  }
}