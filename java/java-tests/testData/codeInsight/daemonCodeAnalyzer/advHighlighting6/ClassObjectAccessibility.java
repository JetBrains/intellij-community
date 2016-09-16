@SomeAnnotation(<error descr="'Foo.Bar' has private access in 'Foo'">Foo.Bar</error>.class)
public class Foo{
  private static class Bar {
  }
}
@interface SomeAnnotation {
    Class value();
}
