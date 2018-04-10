// "Replace condition with Objects.requireNonNullElseGet" "INFORMATION"

import java.util.*;

class Test {
  static class M {
    Object getCodeCache(){return new Object();}
  }

  static M getProviders() {
    return new M();
  }

  static void test(Object context[], Object method, Object compilationResult) {
    Object[] debugContext = context != null ? context :<caret> new Object[]{getProviders().getCodeCache(), method, compilationResult};
  }
}