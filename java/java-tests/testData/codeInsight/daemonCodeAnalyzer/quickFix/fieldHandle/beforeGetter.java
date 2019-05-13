// "Change type to 'int'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findGetter(Test.class, "myInt", <caret>String.class);
  }
}

class Test {
  public int myInt;
}