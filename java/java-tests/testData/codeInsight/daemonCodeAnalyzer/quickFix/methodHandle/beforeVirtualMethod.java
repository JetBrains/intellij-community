// void method()
// String method(String)
// String method(String, String[])
import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findVirtual(Test.class, "method", <caret>MethodType.methodType(String.class));
  }
}

class Test {
  public void method() {}
  public String method(String a) {return a;}
  public String method(String a, String... b) {return a;}
}