// "Replace condition with Objects.requireNonNullElseGet" "INFORMATION"

import java.util.*;

class Test {
  public Object test(Object o) {
    if<caret>(o == null) {
      return new Object();
    } else {
      return o;
    }
  }
}