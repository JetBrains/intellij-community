import java.io.IOException;

class Test {

  interface B<K, E extends Throwable> {
    K l(K k) throws E;
  }

  <R> void bar(B<R, IOException> b) {}

  <E extends Throwable, T> T baz(T l) throws E {
    return null;
  }

  {
    bar(l -> baz(l));
    bar(this::baz);
  }
}
