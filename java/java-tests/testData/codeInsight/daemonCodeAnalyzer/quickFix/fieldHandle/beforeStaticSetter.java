// "Change type to 'int'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStaticSetter(Test.class, "ourInt", <caret>String.class);
  }
}

class Test {
  public static int ourInt;
}