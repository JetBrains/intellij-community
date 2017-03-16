// "Replace with 'findSetter'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findSetter(Test.class, "myInt", int.class);
  }
}

class Test {
  public int myInt;
}