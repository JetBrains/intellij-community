// void method()
// String method(String)
// String method(String, String[])
import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findStatic(Test.class, "method", <caret>MethodType.methodType(void.class, void.class));
  }
}

class Test {
  public static void method() {}
  public static String method(String a) {return a;}
  public static String method(String a, String... b) {return a;}
}