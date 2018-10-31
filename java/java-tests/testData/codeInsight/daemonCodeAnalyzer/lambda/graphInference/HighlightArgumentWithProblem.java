import java.util.List;
import java.util.Set;
class Test2 {
  void foo(String s, Integer p) {}

  <T> T bar(Class<T> c) {
    return null;
  }

  {
    foo (bar(String.class), <error descr="'foo(java.lang.String, java.lang.Integer)' in 'Test2' cannot be applied to '(java.lang.String, java.lang.String)'">""</error>);
  }
}

class M1 {
    void foo(List<String> l) {
        m<error descr="'m(java.util.List<K>, K)' in 'M1' cannot be applied to '(java.util.List<java.lang.String>, java.util.Set<java.lang.Object>)'">(l, n())</error>;
    }
    <K> void m(List<K> list, K k) {}
    <N> Set<N> n() {return null;}
}

class M2 {
    void foo(List<String> l) {
        m<error descr="'m(java.util.List<K>, java.util.List<K>)' in 'M2' cannot be applied to '(java.util.List<java.lang.String>, java.util.Set<java.lang.Object>)'">(l, n())</error>;
    }
    <K> void m(List<K> list, List<K> k) {}
    <N> Set<N> n() {return null;}
}

class M3 {
    void foo(List<String> l) {
        m<error descr="'m(java.util.List<K>, java.util.List<K>)' in 'M3' cannot be applied to '(java.util.List<java.lang.String>, java.util.List<java.lang.Integer>)'">(l, n())</error>;
    }
    <K> void m(List<K> list, List<K> k) {}
    <N> List<Integer> n() {return null;}
}

class M4 {
    void foo(List<String> l) {
        m<error descr="'m(java.util.List<java.lang.Integer>, java.util.List<K>)' in 'M4' cannot be applied to '(java.util.List<java.lang.String>, java.util.List<java.lang.Integer>)'">(l, n())</error>;
    }

    <K> void m(List<Integer> list, List<K> k) {}
    List<Integer> n() {return null;}
}