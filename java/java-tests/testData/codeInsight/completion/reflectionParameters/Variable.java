import foo.bar.*;
import foo.baz.Baz;
class Main {
  void foo() throws ReflectiveOperationException {
    Class<Baz> aType = Baz.class;
    Test.class.getAnnotation(<caret>);
  }
}