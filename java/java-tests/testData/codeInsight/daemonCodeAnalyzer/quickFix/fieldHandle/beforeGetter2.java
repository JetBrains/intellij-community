// "Replace with 'findGetter'" "true"
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.<caret>findStaticGetter(Test.class, "myInt", int.class);
  }
}

class Test {
  public int myInt;
}