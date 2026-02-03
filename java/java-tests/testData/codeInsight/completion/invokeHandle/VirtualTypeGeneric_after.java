import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findVirtual(Types.class, "genericMethod", MethodType.methodType(Object.class, Object.class, String.class));
  }
}