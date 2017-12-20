// "Remove explicit type arguments" "true"
import java.util.*;

class Foo {
  {
    List<String> m = new ArrayList<>(Collections.<St<caret>ring>nCopies(1, null));
  }
}
