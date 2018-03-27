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
    Object[] debugContext = Objects.requireNonNullElseGet(context, () -> new Object[]{getProviders().getCodeCache(), method, compilationResult});
  }
}