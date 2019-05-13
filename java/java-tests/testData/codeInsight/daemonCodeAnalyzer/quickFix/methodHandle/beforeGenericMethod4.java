// Object method(Object)
// Object method(Object, Object[])
// Object method(Object, String)
import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findVirtual(Test.class, "method", <caret>MethodType.genericMethodType(0, true));
  }
}

class Test {
  public <T> T method(T a) {return null;}
  public <T> T method(T a, T... b) {return null;}
  public <T> T method(T a, String b) {return null;}
}