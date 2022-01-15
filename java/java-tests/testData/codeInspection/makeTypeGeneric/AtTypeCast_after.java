import java.util.*;

public class AtTypeCast {
  void test(Object object) {
    if (object instanceof List) {
      ((<caret>List<?>)object).clear();
    }
  }
}