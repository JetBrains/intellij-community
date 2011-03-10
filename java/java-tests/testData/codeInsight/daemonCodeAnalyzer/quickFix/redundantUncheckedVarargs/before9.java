// "Remove 'unchecked' suppression" "true"
import java.util.*;

@SuppressWarnings({"unchecked", "bla-blah-toolid"})
public class Tes<caret>t {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

