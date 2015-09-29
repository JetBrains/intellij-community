// "Remove 'unchecked' suppression" "false"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  static <T> List<T> foo1(T... t){
    return null;
  }

  void foo() {
    //noinspe<caret>ction unchecked
    foo(new ArrayList<String>()).addAll(foo1(new ArrayList<String>()));
  }
}

