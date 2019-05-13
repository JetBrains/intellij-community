import java.io.IOException;
import java.util.List;

class Test {

  interface A<T> {
    T m(T t);
  }

  interface B<K> {
    List<K> l(K k) throws IOException;
  }

  <F> F foo(A<F> a) {
    return null;
  }

  <R> R bar(B<R> b) {
    return null;
  }

  <Z> List<Z> baz(Z l) throws IOException{
    return null;
  }

  {
    Integer i = foo(a -> bar(b -> baz(b)));
  }
}
