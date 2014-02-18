import java.util.List;
import java.util.Set;

class Sample {
  interface L<T> {
    List<T> foo();
  }

  interface S<T> {
    Set<T> foo();
  }

  {
    bar(collect(foo())) ;
  }

  void bar(List<String> l){}

  <T> Set<T> collect(L<T> l, int i){return null;}
  <T1> List<T1> collect(S<T1> l){return null;}

  <K> S<K> foo(){return null;}
}
