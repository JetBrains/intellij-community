// "Apply conversion '.toArray(new java.lang.String[0])'" "false"

import java.util.*;
class Return {
  String[] foo() {
    List<Number> list = new ArrayList<>();
    list.add(1);
    return list<caret>;
  }
}