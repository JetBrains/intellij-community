import java.util.*;

class Test {
  void method(ArrayList<? super Number> p) {
    p.set(0, new Integer(8));
  }
}