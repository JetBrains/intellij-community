import java.lang.invoke.MethodHandle;

class MyTest {
  private void m(MethodHandle handle) {
    I stringSupplier = <error descr="Bad return type in method reference: cannot convert java.lang.Object to java.lang.String">handle::invokeExact</error>;
  }
}
interface I {
  String n() throws Throwable;
}