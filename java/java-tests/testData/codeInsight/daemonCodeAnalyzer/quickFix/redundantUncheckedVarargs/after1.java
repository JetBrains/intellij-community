// "Remove 'unchecked' suppression" "true-preview"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void foo() {
      foo(new ArrayList<String>());
  }
}

