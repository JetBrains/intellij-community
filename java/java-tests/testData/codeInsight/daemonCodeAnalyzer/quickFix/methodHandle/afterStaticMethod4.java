import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();
    l.findStatic(Test.class, "method", MethodType.methodType(void.class));
  }
}

class Test {
  public static void method() {}
}