
import java.util.Map;

class X {
  void someMethod() {
    var <caret>mapOfLong = Map.of(1, 1L, 2, 2L); // refactor this line
  }

}