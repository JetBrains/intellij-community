class Key<T> {
  static <T> Key<T> create() {}
}

interface Intf<T,Loc> {}
class Impl implements Intf<String,String> {}

interface IBar {
  Key<Impl> A_KEY = Key.create("a");
}

class Bar implements IBar {

  {
    IBar item;
    new Foo().incUseCount(<caret>item);
  }
}

class Foo {

  public <T, Loc> void incUseCount(final Key<? extends Intf<T, Loc>> key,
                                                                 final T element,
                                                                 final Loc location) {}
}
