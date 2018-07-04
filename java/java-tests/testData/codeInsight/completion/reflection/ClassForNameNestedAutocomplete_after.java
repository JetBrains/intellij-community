import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Class.forName("foo.bar.PublicClass$NestedClass");
  }
}