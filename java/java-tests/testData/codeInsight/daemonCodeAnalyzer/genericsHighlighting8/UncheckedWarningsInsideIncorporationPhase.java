abstract class Group {

  public Group() {
  }

  public <T extends Category> T get(Key<T> key) {
    return <error descr="Incompatible types. Required T but 'getCategory' was inferred to R:
Incompatible types: Category is not convertible to T">getCategory(key);</error>
  }

  public abstract <R extends Category<R>> R getCategory(Key<R> key);

  public <T extends Category> T get1(Key<T> key) {
    return getCategory1(key);
  }

  public abstract <R extends Category> R getCategory1(Key<R> key);
}

interface Category<Tc extends Category> {
}

class Key<Tk extends Category> {
}

class Test {

  static <T extends B> T get(T b) {
    return create(b);
  }

  public static <K extends A<?, ?>> K create(K a) {
    return a;
  }
}

class A<T, U> {}
class B<R> extends A<Object, R> {}