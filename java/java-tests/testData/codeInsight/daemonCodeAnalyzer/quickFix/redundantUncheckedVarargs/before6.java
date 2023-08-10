// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

@SuppressWarnings("un<caret>checked")
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

