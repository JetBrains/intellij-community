import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findConstructor(Constructed.class, MethodType.methodType(void.class, Object[].class));
  }
}