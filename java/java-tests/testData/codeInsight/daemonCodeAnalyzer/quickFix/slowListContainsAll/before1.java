// "Wrap 'list' in 'HashSet' constructor" "true"
import java.util.Collection;
import java.util.List;

class Test {
  boolean check(List<String> list, Collection<String> collection) {
    return list.containsAll<caret>(collection);
  }
}