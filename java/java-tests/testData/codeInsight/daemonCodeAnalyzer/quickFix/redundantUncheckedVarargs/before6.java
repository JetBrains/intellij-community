// "Remove 'unchecked' suppression" "true"
import java.util.*;

@SuppressWarnings("unchecked")
public class Te<caret>st {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

