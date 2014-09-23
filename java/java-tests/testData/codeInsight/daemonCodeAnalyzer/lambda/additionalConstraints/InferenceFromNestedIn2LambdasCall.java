import java.util.List;

class Test {

  interface A<T> {
    T m(T t);
  }

  interface B<K> {
    List<K> l(K k);
  }

  <F>     F foo(A<F> a) {return null;}
  <Bar> Bar bar(B<Bar> b) { return null;}

  {
    Integer i  = foo(a -> bar(b -> asList(1, b)));
    Integer i1 = foo(a -> bar(b -> asList(1, 1)));
  }

  <L> List<L> asList(L l, L l1) {
    return null;
  }
}
