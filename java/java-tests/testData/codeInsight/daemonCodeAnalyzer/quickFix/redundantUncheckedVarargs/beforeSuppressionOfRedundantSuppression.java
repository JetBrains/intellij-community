// "Remove 'unchecked' suppression" "false"
import java.util.*;

@SuppressWarnings({"RedundantSuppression"})
class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  @SuppressWarnings({"un<caret>checked"})
  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

