import java.util.List;

class Test {
  <T> T foo(List<T> l) {
    return l.get(0);
  }

  void m(List l){
    boolean foo = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">foo(l);</error>
    String s = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">foo(l);</error>
  }
}