// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
      //comment1
    List<ArrayList<String>> list = foo(new ArrayList<String>());

    //noinspection unchecked
    ArrayList<String> list = new ArrayList();
  }
}

