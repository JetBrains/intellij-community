// "Remove type arguments" "true-preview"
import java.util.*;

class Foo {
  {
    List<String> m = new ArrayList<>(Collections.nCopies(1, null));
  }
}
