import java.util.Collections;
import java.util.List;

class Main {
  <T> List<? extends T> get(T t) {
    return Collections.singletonList(t);
  }

  void test() {
    List<caret> list = get(1);
  }
}