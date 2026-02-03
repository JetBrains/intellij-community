import java.util.List;
import java.util.ArrayList;

public class A {
  void foo(List<String> l) {
    if (l instanceof ArrayList) {
      l.ge<caret>
    }
  }
}
