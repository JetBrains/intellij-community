// "Wrap 'list' in 'HashSet' constructor" "true"
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

class Test {
  boolean check(List<String> list, Collection<String> collection) {
    return new HashSet<>(list).containsAll(collection);
  }
}