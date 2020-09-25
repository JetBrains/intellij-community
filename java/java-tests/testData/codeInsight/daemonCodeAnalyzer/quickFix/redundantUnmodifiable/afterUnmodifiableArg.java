// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;

class Main {

  public static void main(String[] args) {
    Collections.EMPTY_LIST;

    Collections.EMPTY_LIST;
    Collections.EMPTY_SET;
    Collections.EMPTY_MAP;

    Collections.EMPTY_SET;
    Collections.EMPTY_MAP;

    Collections.EMPTY_SET;
    Collections.EMPTY_MAP;
  }
}
