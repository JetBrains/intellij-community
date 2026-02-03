import java.util.List;

class Sample {
  interface Fun<A extends List <String>, B> {
    B f(A a);
  }

  <T, R> void foo(Fun<? super T , R> f) {}

  {
    foo((List<String> ls) -> ls.size());
  }
}
