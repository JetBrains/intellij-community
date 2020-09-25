// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;

class Main {

  public static void main(String[] args) {
    Collections.emptyList();

    Collections.emptyList();
    Collections.emptySet();
    Collections.emptyMap();

    Collections.emptySet();
    Collections.emptyMap();

    Collections.emptySet();
    Collections.emptyMap();
  }
}
