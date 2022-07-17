// "Replace condition with Objects.requireNonNullElseGet" "INFORMATION"

import java.util.*;

class Test {
  public Object test(Object o) {
      return Objects.requireNonNullElseGet(o, Object::new);
  }
}