import java.util.*;

public class Test {
  void method(Set<? extends Object> s) {
    s.add(null);
  }
}
