import java.util.*;

class Test {
  void method(ArrayList<? extends Number> p) {
       Number n = p.get(0);
  }
}