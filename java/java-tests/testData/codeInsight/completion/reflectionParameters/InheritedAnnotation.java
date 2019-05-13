import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Test.class.getAnnotation(<caret>);
  }
}