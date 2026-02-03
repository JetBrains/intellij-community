import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStatic(Test.class, "psm1", MethodType.methodType(void.class, char.class));
  }
}