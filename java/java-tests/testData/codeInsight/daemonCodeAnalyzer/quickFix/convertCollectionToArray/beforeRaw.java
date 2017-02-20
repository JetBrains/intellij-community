// "Apply conversion '.toArray()'" "true"

import java.util.*;
class Raw {
  Object[] foo() {
    Set set = new TreeSet();
    Object[] <caret>arr = set;
    return arr;
  }
}