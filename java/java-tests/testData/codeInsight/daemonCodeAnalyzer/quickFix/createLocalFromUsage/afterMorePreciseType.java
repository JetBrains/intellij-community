// "Create local variable 'foo'" "true"
import java.util.*;
class Test {
  {
      String foo<caret>;
      new Bar(Collections.singletonList(foo));
  }

  class Bar {
    Bar(List<String> l) {
    }
  }
}
