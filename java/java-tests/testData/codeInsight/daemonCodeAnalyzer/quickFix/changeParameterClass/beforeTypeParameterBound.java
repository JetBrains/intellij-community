// "Make 'T' implement 'b'" "true-preview"
class a<T> {
    void f(b<String> r, T t) {
        r.g(<caret>t);
    }
}
interface b<T> {
  void g(b<T> t);
}

