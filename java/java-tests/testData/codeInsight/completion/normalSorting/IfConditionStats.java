class AbstractSet<T> {
  public boolean contains(T t) {}
}

class MySet<T> extends AbstractSet<T> {
  public boolean containsAll(Collection<?> c) {}
}

public class Foo {
  MySet<String> set;
  void foo() {
    if (set.<caret>
  }
}