import java.util.*;

class Test<T> {
  Map<Integer, String> map;

  String meth() {
    return map.get(2);
  }
}