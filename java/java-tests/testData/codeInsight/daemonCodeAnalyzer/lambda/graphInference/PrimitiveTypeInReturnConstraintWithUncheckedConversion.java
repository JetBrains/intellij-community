import java.util.List;

class Test {
  <T> T foo(List<T> l) {
    return l.get(0);
  }

  void m(List l){
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'boolean'">boolean foo = foo(l);</error>
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">String s = foo(l);</error>
  }
}