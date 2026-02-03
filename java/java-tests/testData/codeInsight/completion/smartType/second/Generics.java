class Bar<T> {
  T getGoo(int a);
}
class Goo {}

class Foo {
  Bar<Goo> bgetBar() {}


  {
    Goo g = b<caret>
  }
}