import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findVirtual(Test.class, "method", MethodType.methodType(void.class));
  }
}

class Test {
  public void method() {}
}