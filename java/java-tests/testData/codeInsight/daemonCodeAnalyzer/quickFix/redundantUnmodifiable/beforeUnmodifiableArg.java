// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collections unmodifiableCollection = Collections.unmodifiableCollecti<caret>on(Collections.EMPTY_LIST);

    List unmodifiableList = Collections.unmodifiableList(Collections.EMPTY_LIST);
    Set unmodifiableSet = Collections.unmodifiableSet(Collections.EMPTY_SET);
    Map unmodifiableMap = Collections.unmodifiableMap(Collections.EMPTY_MAP);

    SortedSet unmodifiableSortedSet = Collections.unmodifiableSortedSet(Collections.EMPTY_SET);
    SortedMap unmodifiableSortedMap = Collections.unmodifiableSortedMap(Collections.EMPTY_MAP);

    NavigableSet unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(Collections.EMPTY_SET);
    NavigableMap unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(Collections.EMPTY_MAP);
  }
}
