import java.util.*;

public class AtTypeCast {
  void test(Object object) {
    if (object instanceof X) {
      List list = ((<caret>X)object).get();
    }
  }
}
interface X<T> {
  List<T> get();
}
