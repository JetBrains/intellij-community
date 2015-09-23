// "Remove 'unchecked' suppression" "true"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void foo() {
    //noinsp<caret>ection unchecked,blah-blah-toolid
    foo(new ArrayList<String>());
  }
}

