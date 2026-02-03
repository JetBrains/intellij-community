import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Test.class.getDeclaredAnnotation(<caret>);
  }
}