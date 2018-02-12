import java.lang.invoke.*;
import java.util.List;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStatic(Types.class, "sGenericMethod", MethodType.methodType(Object.class, List.class, Object[].class));
  }
}