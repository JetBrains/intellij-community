// "Remove 'ALL' suppression" "true"
import java.util.*;

@SuppressWarnings({"AL<caret>L"})
class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

