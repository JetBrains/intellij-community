// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collection unmodifiableCollection = Collections.unmodifiableCollecti<caret>on(getEmptyList());

    List unmodifiableList = Collections.unmodifiableList(getEmptyList());
    Set unmodifiableSet = Collections.unmodifiableSet(getEmptySet());
    Map unmodifiableMap = Collections.unmodifiableMap(getEmptyMap());

    SortedSet unmodifiableSortedSet = Collections.unmodifiableSortedSet(getEmptySet());
    SortedMap unmodifiableSortedMap = Collections.unmodifiableSortedMap(getEmptyMap());

    NavigableSet unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(getEmptySet());
    NavigableMap unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(getEmptyMap());
  }

  static List getEmptyList() {
    return Collections.emptyList();
  }

  static Set getEmptySet() {
    return Collections.emptySet();
  }

  static Map getEmptyMap() {
    return Collections.emptyMap();
  }
}
