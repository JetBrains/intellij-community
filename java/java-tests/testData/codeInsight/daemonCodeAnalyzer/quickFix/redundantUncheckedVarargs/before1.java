// "Remove 'unchecked' suppression" "true-preview"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void foo() {
    //noinspection unc<caret>hecked
    foo(new ArrayList<String>());
  }
}

