import foo.bar.*;
import foo.baz.Baz;

class Main {
  void foo() throws ReflectiveOperationException {
    More.class.getAnnotation(Baz.class);
  }
}