// "Remove redundant "unchecked" suppression" "false"
import java.util.*;

@SuppressWarnings("unc<caret>hecked")
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    foo(new ArrayList<String>()).addAll(Arrays.asList(new ArrayList<String>);
  }
}

