// "Make 'a' implement 'b'" "false"
class a<T> implements b<T> {
}
interface b<T> { }
class f {
  <K> void g(b<K> kb, java.util.List<K> l) {}
  void m(a<String> aa){
    g(a<caret>a, new Object());
  }
}

