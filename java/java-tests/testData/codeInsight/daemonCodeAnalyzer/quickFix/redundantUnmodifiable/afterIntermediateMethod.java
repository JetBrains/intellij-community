// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Main {

  public static void main(String[] args) {
    getEmptyList();

    getEmptyList();
    getEmptySet();
    getEmptyMap();

    getEmptySet();
    getEmptyMap();

    getEmptySet();
    getEmptyMap();
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
