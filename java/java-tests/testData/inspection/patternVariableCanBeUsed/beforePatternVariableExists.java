// "Replace 'ts' with existing pattern variable 'i1'" "true"
import java.util.*;

class Test {
  interface T {}

  HashSet<T> test(Iterable<T> i) {
    if (i instanceof Collection<T> i1) {
      Collection<T> t<caret>s = (Collection<T>) i;
      return new HashSet<>(ts);
    }
    return null;
  }
}