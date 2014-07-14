import java.util.*;

class Test {
  void method(ArrayList<? super Integer> p) {
    p.set(0, new Integer(8));
  }
}