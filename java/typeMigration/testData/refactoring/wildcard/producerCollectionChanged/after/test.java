import java.util.*;

class Test {
  void method(Set<? super Integer> p) {
    p.add(new Integer(8));
  }
}