// "Create local variable 'foo'" "true"
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
