// "Replace with 'findStaticVarHandle'" "true-preview"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findStaticVarHandle(Test.class, "ourInt", int.class);
  }
}

class Test {
  public static int ourInt;
}