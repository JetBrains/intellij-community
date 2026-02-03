import java.util.function.Consumer;
class Test {
  private static AG<AE> foo(Class clz) {
    return (AG<AE>) foo1(clz);
  }

  private static <T extends E> G<T> foo1(Class<? extends E> clz) {
    return null;
  }

  interface E {}
  interface AE extends E {}

  interface G<T extends E> {}
  interface AG<T extends AE> extends G<T> {}

}

class Test1 {

  static class D<T> {
    public D(Consumer<T> c, Class<?> cl) {
    }

    static <M> D<M> create(Consumer<M> c, Class<?> ck) {
      return new D<>(c, ck);
    }
  }

  {
    Class c = D.class;

    D<String> d = new D<>(s -> s.isEmpty(), c);
    D<String> d1 = D.create(s -> s.isEmpty(), c);
  }
}
class Test2 {

  static class D<T> {
    public D(Consumer<T> c, Class<? extends String> cl) {
    }

    static <M> D<M> create(Consumer<M> c, Class<? extends String> ck) {
      return new D<>(c, ck);
    }
  }

  {
    Class c = D.class;

    D<String> d = new D<>(s -> s.<error descr="Cannot resolve method 'isEmpty' in 'Object'">isEmpty</error>(), c);
    D<String> d1 = D.create(s -> s.<error descr="Cannot resolve method 'isEmpty' in 'Object'">isEmpty</error>(), c);
  }
}