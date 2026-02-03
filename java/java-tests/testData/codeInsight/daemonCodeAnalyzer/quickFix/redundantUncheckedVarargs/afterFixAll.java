// "Fix all 'Redundant suppression' problems in file" "true"
import java.util.ArrayList;

public class Test {
  @SafeVarargs
  static <T> void foo(T... t){
  }

  void foo() {
      foo(new ArrayList<String>());
      foo(new ArrayList<String>());
  }
}

