// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collections unmodifiableCollection = Collections.EMPTY_LIST;

    List unmodifiableList = Collections.EMPTY_LIST;
    Set unmodifiableSet = Collections.EMPTY_SET;
    Map unmodifiableMap = Collections.EMPTY_MAP;

    SortedSet unmodifiableSortedSet = Collections.EMPTY_SET;
    SortedMap unmodifiableSortedMap = Collections.EMPTY_MAP;

    NavigableSet unmodifiableNavigableSet = Collections.EMPTY_SET;
    NavigableMap unmodifiableNavigableMap = Collections.EMPTY_MAP;
  }
}
