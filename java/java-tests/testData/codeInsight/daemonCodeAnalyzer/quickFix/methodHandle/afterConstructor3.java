// Test()
import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findConstructor(Test.class, MethodType.methodType(void.class));
  }
}

class Test {
}