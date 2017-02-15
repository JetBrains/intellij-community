import foo.bar.*;
import foo.baz.Baz;
class Main {
  void foo() throws ReflectiveOperationException {
    Class<Annotation> aType = Baz.class;
    Test.class.getAnnotation(aType);
  }
}