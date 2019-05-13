import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Thread.currentThread().getContextClassLoader().loadClass("foo.bar.PublicClass");
  }
}