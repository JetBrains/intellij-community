import java.util.*;

class Test {
  void method(List<? extends Number> p1, Number p2){
    p1.add(p2);
  }
}