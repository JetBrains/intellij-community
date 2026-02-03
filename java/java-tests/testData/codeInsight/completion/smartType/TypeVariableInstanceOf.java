interface Foo {}
class Bar implements Foo {}
class Goo implements Foo {}

class Main<T extends Foo> {
  {
    T t;
    if (t instanceof <caret>)
  }
}