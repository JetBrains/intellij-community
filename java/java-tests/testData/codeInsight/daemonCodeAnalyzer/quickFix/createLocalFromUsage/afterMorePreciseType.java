// "Create local variable 'foo'" "true-preview"
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
