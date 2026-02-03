import java.util.Collection;

class Foo<T> {

  Collection<Foo<T>> foos() {}

  {
    Foo<T>[] f = <caret>
  }

}
