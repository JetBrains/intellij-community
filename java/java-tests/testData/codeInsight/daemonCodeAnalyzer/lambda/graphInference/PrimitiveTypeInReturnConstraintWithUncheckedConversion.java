import java.util.List;

class Test {
  <T> T foo(List<T> l) {
    return l.get(0);
  }

  void m(List l){
    boolean foo = <error descr="Incompatible types. Required boolean but 'foo' was inferred to T:
Incompatible types: Object is not convertible to boolean">foo(l);</error>
    String s = <error descr="Incompatible types. Required String but 'foo' was inferred to T:
Incompatible types: Object is not convertible to String">foo(l);</error>
  }
}