// "Fix all 'Redundant suppression' problems in file" "true"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void foo() {
    //noinspection un<caret>checked
    foo(new ArrayList<String>());
    //noinspection unchecked
    foo(new ArrayList<String>());
  }
}

