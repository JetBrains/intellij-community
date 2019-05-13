// Test()
// Test(int)
import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findConstructor(Test.class, <caret>MethodType.methodType(void.class, String.class));
  }
}

class Test {
  public Test() {}
  public Test(int a) {}
}