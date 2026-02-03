import java.util.*;

class Test {
  void method(List<? super Integer> p1, Number p2){
    p1.add(p2);
  }
}