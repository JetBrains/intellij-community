// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  @SuppressWarnings("unchecked")
  void fo<caret>o() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

