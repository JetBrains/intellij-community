import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Foo {
  
  Map<String, Integer> foo() {
    return Bar.new<caret>
  }
}

class Bar {
  static <T, V> HashMap<T, V> newMap() {}
  static <E> ArrayList<E> newList() {}
}