// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    List list = new ArrayList();
    list = Collections.emptyList();

    Set set = new HashSet();
    set = Collections.emptySet();

    Map map = new HashMap();
    map = Collections.emptyMap();

    Collections.unmodifiableCollecti<caret>on(list);

    Collections.unmodifiableList(list);
    Collections.unmodifiableSet(set);
    Collections.unmodifiableMap(map);

    Collections.unmodifiableSortedSet(set);
    Collections.unmodifiableSortedMap(map);

    Collections.unmodifiableNavigableSet(set);
    Collections.unmodifiableNavigableMap(map);
  }
}
