// "Fix all 'Immutable collection creation can be replaced with collection factory call' problems in file" "true"
import java.util.*;

class Main {
  private final List<String> myList;
  private final List<String> myList3;
  private final Map<String, String> myMap;
  private final Set<String> mySet;
  private final Set<String> mySet2;

  Main(Collection<String> list, Map<? extends String, ? extends String> map,
       Set<String> set) {
    myList = List.copyOf(list);
    myList2 = List.copyOf(set);
    myMap = Map.copyOf(map);
    mySet = Set.copyOf(set);
    mySet2 = Set.copyOf(list);
  }
}