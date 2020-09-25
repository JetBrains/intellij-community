// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;

class Main {

  public static void main(String[] args) {
    Collections.unmodifiableCollecti<caret>on(Collections.EMPTY_LIST);

    Collections.unmodifiableList(Collections.EMPTY_LIST);
    Collections.unmodifiableSet(Collections.EMPTY_SET);
    Collections.unmodifiableMap(Collections.EMPTY_MAP);

    Collections.unmodifiableSortedSet(Collections.EMPTY_SET);
    Collections.unmodifiableSortedMap(Collections.EMPTY_MAP);

    Collections.unmodifiableNavigableSet(Collections.EMPTY_SET);
    Collections.unmodifiableNavigableMap(Collections.EMPTY_MAP);
  }
}
