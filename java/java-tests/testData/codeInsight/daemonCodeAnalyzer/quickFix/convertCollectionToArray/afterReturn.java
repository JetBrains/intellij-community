// "Apply conversion '.toArray(new java.lang.String[0])'" "true"

import java.util.*;
class Return {
  String[] foo() {
    List<String> list = new ArrayList<>();
    list.add("s");
    return list.toArray(new String[0]);
  }
}