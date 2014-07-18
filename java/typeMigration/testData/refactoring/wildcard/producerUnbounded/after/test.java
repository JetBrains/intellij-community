import java.util.*;

class Test {
  void method(ArrayList<?> p) {
    p.set(0, new Integer(8));
  }
}