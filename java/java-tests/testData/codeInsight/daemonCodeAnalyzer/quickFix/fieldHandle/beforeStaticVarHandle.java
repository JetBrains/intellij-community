// "Change type to 'int'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStaticVarHandle(Test.class, "ourInt", <caret>boolean.class);
  }
}

class Test {
  public static int ourInt;
}