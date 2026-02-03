import java.util.*;
public class Test {
  void method(List<? extends Number> l) {
    for (Number integer : l) {
      System.out.println(integer.intValue());
    }
  }
}
