import foo.*;

class Goo {
  void method(Foo o) {
    if (o instanceof FooImpl) {
      ((FooImpl) o).consume();<caret>
    }
  }
}