import java.util.*;

class Test<T> {
  Map<Integer, String> map;

  T meth() {
    return map.get(2);
  }
}