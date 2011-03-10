// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void fo<caret>o() {
    //noinspection unchecked
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

