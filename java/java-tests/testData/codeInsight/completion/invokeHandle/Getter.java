import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findGetter(Test.class, "<caret>");
  }
}