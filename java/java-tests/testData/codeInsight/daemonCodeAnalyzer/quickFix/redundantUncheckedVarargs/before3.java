// "Remove redundant "unchecked" suppression" "false"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    //noinspection unc<caret>hecked
    foo(new ArrayList<String>()).addAll(Arrays.asList(new ArrayList<String>()));
  }
}

