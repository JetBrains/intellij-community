// void method()
// String method(String)
// String method(String, String[])
import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findVirtual(Test.class, "method", MethodType.methodType(String.class, String.class, String[].class));
  }
}

class Test {
  public String method(String a, String... b) {return a;}
  public void method() {}
  public String method(String a) {return a;}
}