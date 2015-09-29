class A<T> {}
class Foo<K extends A<K>> {
  {
    Foo foo = new Foo<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>() {};
  }
}