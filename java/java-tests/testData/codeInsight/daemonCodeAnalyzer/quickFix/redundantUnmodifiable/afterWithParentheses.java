// "Fix all 'Redundant usage of unmodifiable collection wrappers' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    Collection unmodifiableCollection = ((Collections.emptyList()));

    List unmodifiableList = ((Collections.emptyList()));
    Set unmodifiableSet = ((Collections.emptySet()));
    Map unmodifiableMap = ((Collections.emptyMap()));

    SortedSet unmodifiableSortedSet = ((Collections.emptySet()));
    SortedMap unmodifiableSortedMap = ((Collections.emptyMap()));

    NavigableSet unmodifiableNavigableSet = ((Collections.emptySet()));
    NavigableMap unmodifiableNavigableMap = ((Collections.emptyMap()));
  }
}
