// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

/**
 * @noinspection a1,unch<caret>ecked, b2
 */
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

