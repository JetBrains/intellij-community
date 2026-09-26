// "Remove 'unchecked' suppression" "true-preview"
import java.util.*;

/// Ahah guys did you know that if the tag is on the first line, it fails to remove an instance of the prefix ?
/// Happens on both classic and Markdown docs by the way
/// unrelated text
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

