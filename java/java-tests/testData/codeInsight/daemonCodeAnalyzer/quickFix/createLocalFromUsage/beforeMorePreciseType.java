// "Create local variable 'foo'" "true-preview"
import java.util.*;
class Test {
  {
    new Bar(Collections.singletonList(fo<caret>o));
  }

  class Bar {
    Bar(List<String> l) {
    }
  }
}
