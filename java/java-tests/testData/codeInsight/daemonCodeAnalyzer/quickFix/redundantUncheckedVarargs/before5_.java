// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  
  void foo() {
    @SuppressWarnings("unch<caret>ecked")
    List<ArrayList<String>> list = foo(new ArrayList<String>()), list1 = null;
  }
}

