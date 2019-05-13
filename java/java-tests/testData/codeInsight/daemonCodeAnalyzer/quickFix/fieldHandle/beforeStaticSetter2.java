// "Replace with 'findStaticSetter'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.<caret>findSetter(Test.class, "ourInt", int.class);
  }
}

class Test {
  public static int ourInt;
}