// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collection unmodifiableCollection = getEmptyList();

    List unmodifiableList = getEmptyList();
    Set unmodifiableSet = getEmptySet();
    Map unmodifiableMap = getEmptyMap();

    SortedSet unmodifiableSortedSet = getEmptySet();
    SortedMap unmodifiableSortedMap = getEmptyMap();

    NavigableSet unmodifiableNavigableSet = getEmptySet();
    NavigableMap unmodifiableNavigableMap = getEmptyMap();
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
