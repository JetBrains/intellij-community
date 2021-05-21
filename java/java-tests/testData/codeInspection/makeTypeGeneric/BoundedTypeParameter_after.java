import java.util.Collections;
import java.util.List;

class Main {
  <T> List<? extends T> get(T t) {
    return Collections.singletonList(t);
  }

  void test() {
    List<? extends Integer> list = get(1);
  }
}