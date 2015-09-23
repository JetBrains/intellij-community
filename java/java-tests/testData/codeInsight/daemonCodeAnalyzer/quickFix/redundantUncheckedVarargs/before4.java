// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    //noinspe<caret>ction unchecked
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

