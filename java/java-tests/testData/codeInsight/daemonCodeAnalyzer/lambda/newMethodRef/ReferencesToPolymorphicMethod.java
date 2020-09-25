import java.lang.invoke.MethodHandle;

class MyTest {
  private void m(MethodHandle handle) {
    I stringSupplier = handle::invokeExact;
  }
}
interface I {
  String n() throws Throwable;
}