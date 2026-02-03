import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Construct.class.getConstructor(<caret>);
  }
}