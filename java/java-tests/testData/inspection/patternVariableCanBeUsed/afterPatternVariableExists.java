// "Replace 'ts' with existing pattern variable 'i1'" "true"
import java.util.*;

class Test {
  interface T {}

  HashSet<T> test(Iterable<T> i) {
    if (i instanceof Collection<T> i1) {
        return new HashSet<>(i1);
    }
    return null;
  }
}