import java.util.Collection;

class X {
  public Foo test(Collection<Foo> collection) {
    Foo result = null;
    int resultWeight = -1;
    for (Foo foo : collection) {
      int fooWeight = foo.getWeight();
      if (result == null || resultWeight <= fooWeight) {
        result = foo;
        resultWeight = fooWeight;
      }
    }
    if (result == null) {
      throw new RuntimeException();
    }
    return result;
  }
}

interface Foo {
  int getWeight();
}
