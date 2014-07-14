import java.util.List;
import java.util.Set;

class Clazz {
    void foo(List<String> l, Set<String> s) {
      l.removeAll(s);
    }
}