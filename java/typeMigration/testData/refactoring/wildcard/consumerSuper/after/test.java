import java.util.*;

class Test {
  void method(ArrayList<? super Number> p, Object p2) {
    p2 = p.get(0);
  }
}