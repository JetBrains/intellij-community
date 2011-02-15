// "Remove redundant "unchecked" suppression" "false"
import java.util.*;

@SuppressWarnings("AL<caret>L")
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

