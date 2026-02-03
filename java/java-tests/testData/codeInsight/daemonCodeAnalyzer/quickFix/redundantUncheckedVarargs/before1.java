// "Remove 'unchecked' suppression" "true-preview"
import java.util.ArrayList;

class Test {
  @SafeVarargs
  private static <T> void foo(T... t){
  }

  public void foo() {
    //noinspection unc<caret>hecked
    foo(new ArrayList<String>());
  }
}

