// "Remove 'unchecked' suppression" "true"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void fo<caret>o() {
    //noinspection unchecked
    foo(new ArrayList<String>());
  }
}

