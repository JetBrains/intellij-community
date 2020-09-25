// "Fix all 'Redundant usage of unmodifiable collection factories' problems in file" "true"

import java.util.*;

class Main {

  public static void main(String[] args) {
    List list = new ArrayList();
    list = Collections.emptyList();

    Set set = new HashSet();
    set = Collections.emptySet();

    Map map = new HashMap();
    map = Collections.emptyMap();

    list;

    list;
    set;
    map;

    set;
    map;

    set;
    map;
  }
}
