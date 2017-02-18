// "Apply conversion '.toArray(new java.lang.String[0])'" "true"

import java.util.*;
class Expression {
  String[] foo() {
    String[] arr;
    arr<caret> = Collections.<String>singleton("s");
    return arr;
  }
}