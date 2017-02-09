import foo.bar.*;
class Main {
  void foo() throws ReflectiveOperationException {
    Class<Annotation> aType = Baz.class;
    Test.class.getAnnotation(Foo.class);
  }
}