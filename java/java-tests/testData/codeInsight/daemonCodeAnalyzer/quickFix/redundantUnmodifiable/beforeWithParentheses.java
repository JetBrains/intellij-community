// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;

class Main {

  public static void main(String[] args) {
    Collections.unmodifiableCollecti<caret>on(((Collections.emptyList())));

    Collections.unmodifiableList(((Collections.emptyList())));
    Collections.unmodifiableSet(((Collections.emptySet())));
    Collections.unmodifiableMap(((Collections.emptyMap())));

    Collections.unmodifiableSortedSet(((Collections.emptySet())));
    Collections.unmodifiableSortedMap(((Collections.emptyMap())));

    Collections.unmodifiableNavigableSet(((Collections.emptySet())));
    Collections.unmodifiableNavigableMap(((Collections.emptyMap())));
  }
}
