// "Make 'a' implement 'b'" "true-preview"
class a {
    void f(b<String> r) {
        r.g(<caret>this);
    }
}
interface b<T> {
  void g(b<T> t);
}

