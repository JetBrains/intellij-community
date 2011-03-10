// "Remove 'unchecked' suppression" "true"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void f<caret>oo() {
    //noinspection unchecked,blah-blah-toolid
    foo(new ArrayList<String>());
  }
}

