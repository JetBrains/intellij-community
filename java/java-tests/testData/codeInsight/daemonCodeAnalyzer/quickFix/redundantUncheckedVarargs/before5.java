// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  @SuppressWarnings("unch<caret>ecked")
  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

