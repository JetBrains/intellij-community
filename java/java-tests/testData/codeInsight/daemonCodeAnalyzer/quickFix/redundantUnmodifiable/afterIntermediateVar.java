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

    Collection unmodifiableCollection = list;

    List unmodifiableList = list;
    Set unmodifiableSet = set;
    Map unmodifiableMap = map;

    SortedSet unmodifiableSortedSet = set;
    SortedMap unmodifiableSortedMap = map;

    NavigableSet unmodifiableNavigableSet = set;
    NavigableMap unmodifiableNavigableMap = map;
  }
}
