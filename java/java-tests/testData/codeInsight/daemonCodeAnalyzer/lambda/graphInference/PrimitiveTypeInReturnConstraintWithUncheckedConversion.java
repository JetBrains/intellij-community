import java.util.List;

class Test {
  <T> T foo(List<T> l) {
    return l.get(0);
  }

  void m(List l){
    boolean foo = foo<error descr="'foo(java.util.List<T>)' in 'Test' cannot be applied to '(java.util.List)'">(l)</error>;
    String s = foo<error descr="'foo(java.util.List<T>)' in 'Test' cannot be applied to '(java.util.List)'">(l)</error>;
  }
}