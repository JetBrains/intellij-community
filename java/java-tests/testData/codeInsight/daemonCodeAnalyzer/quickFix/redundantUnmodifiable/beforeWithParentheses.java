// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collection unmodifiableCollection = Collections.unmodifiableCollecti<caret>on(((Collections.emptyList())));

    List unmodifiableList = Collections.unmodifiableList(((Collections.emptyList())));
    Set unmodifiableSet = Collections.unmodifiableSet(((Collections.emptySet())));
    Map unmodifiableMap = Collections.unmodifiableMap(((Collections.emptyMap())));

    SortedSet unmodifiableSortedSet = Collections.unmodifiableSortedSet(((Collections.emptySet())));
    SortedMap unmodifiableSortedMap = Collections.unmodifiableSortedMap(((Collections.emptyMap())));

    NavigableSet unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(((Collections.emptySet())));
    NavigableMap unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(((Collections.emptyMap())));
  }
}
