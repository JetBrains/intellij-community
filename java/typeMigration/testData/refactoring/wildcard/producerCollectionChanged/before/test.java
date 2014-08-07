import java.util.*;

class Test {
  void method(ArrayList<? super Number> p) {
    p.add(new Integer(8));
  }
}