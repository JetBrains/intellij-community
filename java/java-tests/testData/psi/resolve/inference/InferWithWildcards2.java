class Collection<T> {}

interface Bar {}

class BarImpl implements Bar {}

interface Foo<E extends Bar> {
  Collection<E> getElements();
}

class FooImpl implements Foo<BarImpl> {
  public Collection<BarImpl> getElements() {
    return null;
  }
}

public class Bazz {
  public static <E extends Bar> Collection<E> getElements(Collection<? extends Foo<E>> foos) {
    return null;
  }

  public static void failure(final Collection<FooImpl> foos) {
    <ref>getElements(foos);
 }
}