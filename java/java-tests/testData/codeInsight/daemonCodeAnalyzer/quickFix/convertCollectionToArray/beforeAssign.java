// "Apply conversion '.toArray(new java.lang.String[0])'" "true"

import java.util.*;
class Assign {
  String[] foo() {
    Set<String> set = Collections.singleton("s");
    String[] arr;
    <caret>arr = set;
    return arr;
  }
}