// "Wrap 'list' in 'HashSet' constructor" "false"
import java.util.Collection;
import java.util.List;

class Test {
  boolean check(List<String> list, Collection<String> collection) {
    if (!list.isEmpty()) return true;
    return list.containsAll<caret>(collection);
  }
}