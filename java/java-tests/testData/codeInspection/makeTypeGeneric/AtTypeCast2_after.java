import java.util.*;

public class AtTypeCast {
  void test(Object object) {
    if (object instanceof List) {
      Object element = ((<caret>List<?>)object).get();
    }
  }
}
