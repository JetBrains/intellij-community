// "Remove 'unchecked' suppression" "true-preview"
import java.util.ArrayList;

class Test {
  @SafeVarargs
  private static <T> void foo(T... t){
  }

  public void foo() {
      foo(new ArrayList<String>());
  }
}

