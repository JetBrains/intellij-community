// "Make 'T' implement 'b'" "true"
class a<T extends b<String>> {
    void f(b<String> r, T t) {
        r.g(t);
    }
}
interface b<T> {
  void g(b<T> t);
}

