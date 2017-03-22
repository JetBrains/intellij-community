import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStatic(Types.class, "sObjMethod", MethodType.methodType(Object.class, Object.class));
  }
}