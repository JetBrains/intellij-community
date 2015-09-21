import java.util.List;

class Foo<E extends List<String> & Runnable> {
  Foo() {}

  {
    Foo foo = new Foo<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
  }
}