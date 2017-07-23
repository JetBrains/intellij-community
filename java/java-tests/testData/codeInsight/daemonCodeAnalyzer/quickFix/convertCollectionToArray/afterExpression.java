// "Apply conversion '.toArray(new java.lang.String[0])'" "true"

import java.util.*;
class Expression {
  String[] foo() {
    String[] arr;
    arr = Collections.<String>singleton("s").toArray(new String[0]);
    return arr;
  }
}