// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    List list = new ArrayList();
    list = Collections.emptyList();

    Set set = new HashSet();
    set = Collections.emptySet();

    Map map = new HashMap();
    map = Collections.emptyMap();

    Collection unmodifiableCollection = Collections.unmodifiableCollecti<caret>on(list);

    List unmodifiableList = Collections.unmodifiableList(list);
    Set unmodifiableSet = Collections.unmodifiableSet(set);
    Map unmodifiableMap = Collections.unmodifiableMap(map);

    SortedSet unmodifiableSortedSet = Collections.unmodifiableSortedSet(set);
    SortedMap unmodifiableSortedMap = Collections.unmodifiableSortedMap(map);

    NavigableSet unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(set);
    NavigableMap unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(map);
  }
}
