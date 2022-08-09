// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

   @SuppressWarnings("unchecked")
  void foo() {
       List<ArrayList<String>> list = foo(new ArrayList<String>());

    ArrayList<String> list = new ArrayList();
  }
}

